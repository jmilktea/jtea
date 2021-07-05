## 背景  
实际项目中有一个需求，需要把未分配的数据，按照一定的排序，分配下去给对应人员作业，具体到表里大概是这样子：  
![image]()  

status=0并且uid为空的表示还没分配给人，有值的表是已经分配给人。每天都个定时任务扫描未分配的数据，按照一定的排序，进行分配，sql如下： 
```
select * from t_table where status = 0 order by field_1 asc,field_2 desc
```
field_1 asc,field_desc 这个是根据实际业务要求。每天定时任务不一定能把数据分配下去，也就是会有很久以前的数据，每天都需要尝试去分配。至于为什么总是分配不下去，跟具体业务有关，如果那个区域
没安排人就分配不下去，每天都尝试去分是因为业务随时可能安排那个区域的人员。  

出现的问题主要是：  
目前这个表的数据已经接近千万，待分配的大概有8w，status=0 不能走索引，会进行全表扫描    
这里本来是打算只查最近一段时间的分配数据，例如一个月，这样可以在时间上建立索引，sql为：  
```
select * from t_table where status = 0 where create_time > 20210605 order by field_1 asc,field_2 desc  
```
但是业务方不肯折衷，这个时间定太小对业务有影响，定太大嘛对索引的帮助又不大了。    
分表的做法不再本次讨论范围内，最后我们的做法是，把这个待分配数据存放到redis，使用zset进行存储。   

## 解决方案   
zset的原理我们之前已经分析过，参见这里：[zset]()    
我们的做法是使用zset存储待分配的数据，已分配会从集合删除。zset存储的是value是表的id，score需要根据field_1，field_2设计。  
8w的数据，主键是bigint类型，大约需要占用10M，预估地址：http://www.redis.cn/redis_memory/  
![image]()    

order by field_1 asc,field_2 desc 怎么设计score呢？  
field_1越小越优先，但是又受field_2影响，为了去除这个影响，我们可以乘以一个很大的数，然后再减去field_2，也就是field_1相同，field_2越大score就越小。  
最终是score=field_1 * 1000000000 - feild_2   

### 初始化数据  
开始需要把现有的数据跑到redis中去，我们可以加一个定时任务跑一下，使用的是redis的ZADD命令，代码如下，这里我们加20w的数据  
```
@Test
public void testZSetAdd() {
   long now = System.currentTimeMillis();
   for (int i = 0; i < 200000; i++) {
      redisTemplate.opsForZSet().add("zset_key", i, RandomUtils.nextInt(0, 1000000000));
   }
   System.out.println("use ms:" + (System.currentTimeMillis() - now));
}
```  
这个跑完需要超过20min，效率非常慢。主要消耗是在网络请求上，改成批量添加如下：  
```
@Test
public void testZSetAddBatch() {
   Set<ZSetData> set = Sets.newHashSet();
   long now = System.currentTimeMillis();
   for (int i = 0; i < 200000; i++) {
      ZSetData zSetData = new ZSetData(Long.valueOf(i));
      set.add(zSetData);
      if (set.size() == 20) {
         redisTemplate.opsForZSet().add("zset_key_2", set);
         set.clear();
      }
   }
   System.out.println("use ms:" + (System.currentTimeMillis() - now));
}
```  
只需要75s，批量操作可以减少网络往返时间，ZADD命令本身就支持批量添加，类似的redis还有一个Pipeline也可以批量执行命令。  

### 分页   
redis的核心命令是单线程执行的，我们不能一次加载所有数据到内存，这样消耗的时间会比较久，阻塞其它命令的操作。  
zset支持分页遍历，使用的是ZRANGE命令，示例代码如下：  
```
@Test
public void testZSetRange() {
   long now = System.currentTimeMillis();
   for (int i = 0; i < 200000; i++) {
      Set set = redisTemplate.opsForZSet().range("zset_key3", i * 1000, i * 1000 + 1000);      
      if (set.size() == 0) {
         break;
      }
   }
   System.out.println("use ms:" + (System.currentTimeMillis() - now));
}
```
遍历完20w数据只需要2s，速度非常快。  

### 移除和评分  
如果数据分配下去了，那么就需要从zset移除，使用的是ZREM命令，同样使用批量操作可以提升效率。  
如果field_1和field_2字段的值改变了，排序就会改变，这个时候需要使用ZADD重新添加到zset，如果已经存在，redis只会修改排名。  

通过如上做法，我们可以使用zset存储数据，只存储id占用的内存很小。再通过每次遍历一部分数据，效率非常快。  
需要注意的是，需要考虑在遍历过程中field_1,field_2改变的情况，因为一改变评分就变了，顺序就变了，这样分页会获取到重复数据或者漏掉一部分数据。  






