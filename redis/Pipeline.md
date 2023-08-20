# 前言
本篇来介绍一下redis pipeline，主要是由于最近一次在帮开发同学review代码的时候，发现对redis有个循环操作可以优化。场景大概是这样的，根据某个uid要从redis查询一批数据，每次大概1000个key左右，如果查得到就返回，否则查db，然后写回缓存。由于每次要查的key比较多，虽然redis单次查询很快，但如果key很多，每次查询redis都需要读写socket，与client间的网络数据传输，都需要消耗时间，累加起来也会变得非常慢。开发同学决定使用批量的方式，例如每次操作100个key，使用RedisTemplate批量查询代码如下：   
```
redisTemplate.opsForValue().multiGet(keys);
```
如果查询到的是null，则表示缓存不存在或过期，则查询数据库，再批量写回redis，伪代码如下：  
```
for (Long id : list) {
    operations.opsForValue().set("key", id, 30, TimeUnit.MINUTES);
}
```
他并没有使用批量的方式，如果有100个，这里就需要执行100次set命令，经过了解后原因是批量写入并不能设置过期时间，我们看它的api确实只能设置key-value，但没有过期时间也是不行的。
```
void multiSet(Map<? extends K, ? extends V> map);
```
单个循环设置肯定不行，除了自己执行方法会比较慢，影响用户体验，可能导致接口超时外，由于redis是单线程执行命令的，还会影响其它命令的执行，所以必须优化。    
优化的方式就是本篇要介绍的：pipeline。

# pipeline
pipeline是管道的意思，它最主要的作用就是降低RRT(client-server数据传输往返时间)。在请求-响应过程，除了传递我们的数据，还需要协议信息，例如http协议的请求头，响应头，这些信息也会增加传输时间。举个例子，假设一次RRT是10ms，那么执行10条命令，就需要100ms，如果我们将其打包到一起执行，RRT就还是10ms（虽然传输的数据变多了，但协议本身的信息没有变多，基本可以忽略不计），传输效率提升了10倍。除此之外，redis server每次处理命令都需要对Socket进行IO操作，这涉及到用户态、内核态的切换，如果批量进行处理，对性能的提升也很有帮助。    
pineline将一批命令打包一起执行，但不保证他们的原子性，不像事务一样可以保证一起成功或失败，可能前面的命令执行成功了，后面的执行失败。   
这和我们平时操作数据库的思想是一样的，单个查询转换为批量查询，单个插入转换为批量插入，同样需要注意是，批量虽好，但不能一次过多，否则处理起来比较久，反而得不偿失。    
更多的知识可参考官方文档：https://redis.io/docs/manual/pipelining/   

我们使用springboot 2.x版本，使用spring-boot-starter-data-redis，它给我们默认集成的redis client是lettuce。在使用一个不熟悉或比较新的东西的时候，本人有一个习惯，会先google一下，例如：“RedisTemplate pipeline 注意事项”，“RedisTemplate pipeline 坑”，看看有没有前人踩过坑，借鉴一下。这次也一样，google之后果然发现有点坑，例如[这篇](https://emacsist.github.io/2019/07/30/spring-data-redis%E4%B8%8Elettuce-%E4%BD%BF%E7%94%A8-pipeline-%E6%97%B6%E6%B3%A8%E6%84%8F%E4%BA%8B%E9%A1%B9/)提到的Spring Data Redis与Lettuce使用pipeline时，实际命令并不是一起执行的，有时是单条执行，有时是合并几条执行。       
![image](https://github.com/jmilktea/jtea/blob/master/redis/images/pipeline-1.png)    

我们自己写下测试代码如下：    
```
redisTemplate.executePipelined(new SessionCallback<Object>() {

	@Override
	public Object execute(RedisOperations operations) throws DataAccessException {
		for (int i = 0; i < 100; i++) {
			operations.opsForValue().set("testPipeline2" + i, i, 1, TimeUnit.MINUTES);
		}
		return null;
	}
});
```
在set位置打个断点，然后到redis server使用monitor命令观察，看命令到底是不是一条一条给过来的。monitor命令会将server执行的命令都打印出来，生产环境慎用。   
按照上面的分析，正常情况下这些命令应该是一起发送到server端一起执行的，不会断断续续，但实际我们观察确实不是一起给过来，断断续续的，如下：   
![image](https://github.com/jmilktea/jtea/blob/master/redis/images/pipeline-2.png)    

我们把lettuce替换成jedis看看。   
```
<dependency>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-data-redis</artifactId>
	<exclusions>
		<exclusion>
                    <groupId>io.lettuce</groupId>
                    <artifactId>lettuce-core</artifactId>
                </exclusion>
	</exclusions>
</dependency>
<dependency>
	<groupId>redis.clients</groupId>
	<artifactId>jedis</artifactId>
</dependency>
```
还是执行上面的代码，打断点，使用jedis可以观察到，每次循环monitor都不会观察到有命令执行，直到最后才一批给过来。   
![image](https://github.com/jmilktea/jtea/blob/master/redis/images/pipeline-3.png)   

但我们不想直接替换lettuce为jedis，一个是它是spring boot默认集成的，拥有更好的性能，二是替换后不知道其它功能有没有影响，那怎么办呢？    
我们项目还使用redission分布式锁，其实redission也是一个redis client，理论上它应该实现所有client的功能，pipeline自然也有实现。    
我们使用redission如下：   
```
RBatch batch = redissonClient.createBatch();
for (int i = 0; i < 100; i++) {
	batch.getBucket("testBatch" + i).setAsync(i, 1, TimeUnit.MINUTES);
}
batch.execute();
```
这次我们把断点打在execute位置，看看是不是execute时才一起提交到server执行，答案显然是的。   
![image](https://github.com/jmilktea/jtea/blob/master/redis/images/pipeline-4.png)    

接下来我们简单测试一下性能差距，分别是单个请求，使用lettuce，使用jedis，使用redission，执行10000次，耗时如下：    
单个请求：73029ms   
lettuce: 712ms  
jedis: 413ms  
redission: 341ms

lettuce出乎意料执行还是很快，就想上面提到的，它有时还是会部分打包一起执行，但终究不是一次执行，有兴趣的可以深入了解一下。    

