某天需求上线后，运维反馈redis带宽满了      
由于需求上线后，有手动触发一个定时任务，运维反馈的时间节点和手动触发的时间节点基本一致，所以可以快速定位到是定时任务所在的代码导致     
为了重新，再次手动触发一次，果然又出现了，可以从监控平台看到，最后一次网络高峰大概是23M左右     

![image](https://github.com/jmilktea/jmilktea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/redis-newwork1.png)    


定位代码      
network io 分为输入、输出两种，从图中可以看到input是绿色的线，很平稳，证明写入的流量正常。output的线是黄色的线，有明显的起伏，已经达到了二十多三十兆，证明这段时间读取的数据非常大，例如可能是读取了大key，或者是没有分页读取一个大的集合。     
定位到代码位置，总共有两处读reids，第一处如下：   
```
redisTemplate.opsForZSet().range(key, 0, size);     
```  
代码的本意就是分页读取一个zset集合，集合里面存的是long型id，range对应的redis命令是[zrange](https://redis.io/commands/zrange)     
起初非常怀疑是分页出了问题，例如size过大，或者size越来越大，就非常符合监控，但size的计算非常简单，而且打印的日志也证明size每次只有1000条，1000个long也就不到8kb，不会出现那么大的网络io。    
也怀疑是zrange分页命令用得不对，但这个写法之前也用过，也经过测试，不会出现问题。    

另一处代码是    
```
List<Template> templates = redisTemplate.opsForList().range(key, 0, -1);
if (CollectionUtils.isEmpty(templates)) {
	templates = templateMapper.getAll();
	redisTemplate.opsForList().rightPushAll(key, templates);
	redisTemplate.expire(key, HOUR_8, TimeUnit.SECONDS);	
}
```    
可以看到是为了拿一个模板数据，如果从缓存拿不到，就从数据库拿，然后放到缓存。         
模板的数据只有十几条，从数据库看占用到30kb，按理说也不会出现上述的情况。    

由于监控出现的问题的时间和我们触发的时间完全一致，所以问题肯定出现在这两处地方。    
经过代码排查后，发现第二处是并发操作的，开发同学开了16个线程在跑任务，而上诉代码并没有考虑到并发问题，在多个线程同时执行，可能同时获取不到模板，同时从数据库查询，然后再放到缓存，由于开发用的是rightPushAll，所以每次都是追加在List集合的后面，也就是说这里可能会有问题。    

我们登录redis，使用[LLen](https://redis.io/commands/llen)命令可以看到集合的长度，也可以用arthas    
```
ognl -c 49c2faae '#context=@com.my.ApplicationContextUtils@getContext(),#context.getBean("redisTemplate").opsForList().size("key")'      
```
获取到的长度果然不只十几条，有143条。
另外可以看到，开发同学是把整个模板，也就是整条记录都缓存，而实际只用到了几个字段，有很多字段是不需要的，白白占用了缓存空间。    

那么这143条记录是否就是会占用这么多带宽呢？我们仍需要证明一下。   
需要知道这143条记录在redis大概占用了多少空间，使用命令如下：   
```
memory usage key samples 143
```    
memory usage命令用于估算一个key所占用的空间，它是通过samples采样得出结果的，采样的量越大，当然越精确，一个集合类型可能有非常多元素，要完整知道占用空间就需要知道每个元素的大小，显然这个太消耗时间了，通过采用可以得到一个估算值。也可以使用在线工具进行[容量预估](http://www.redis.cn/redis_memory/)           
这里我们的集合比较小，估算后大概是100k，另外通过日志可以看到，这段代码在1分钟内执行了15次，总共有16个线程，那么1min内从redis获取的数据量大概就是：   
100kb * 15 * 16 约为 23.5m，符合监控的数据。    

找到问题了，解决方式就非常简单了    
1.不需要的数据不要缓存，不要一股脑把整个对象都缓存到redis，只缓存必须的数据，内存是redis最宝贵的资源   
2.解决并发问题，有多少数据就缓存多少     

这也提醒了我们，由于redis是单线程的，所以使用的时候要非常小心，一个命令就可能拖垮所有服务，所以在操作时要时刻考虑redis命令的耗时，例如不要使用大key，集合元素不要太多，读取集合不要一次读取太多元素等。    
