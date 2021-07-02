## 简介
zset是有序的set，它存储了不重复的元素，并且按照score从小到大进行排序。  
实际中有很多排序的场景，如新闻热榜，热门博客等，这类排序场景都可以使用zset来实现。  

常用命令   
- ZADD key score member [[score member] [score member] …]   
添加成员，时间复杂度O(M*log(N))，M是添加成功的个数，如果只添加一个，时间复杂度就是O(log(N))  
单个添加：zadd key_zset 1 member1  
批量添加：zadd key_zset 2 member2 3 member3   
如果需要操作的数量比较多，使用批量操作可以提高性能  

- ZSCORE key member  
返回对应member的score,时间复杂度是O(1)  
zscore key_zset member1   
为什么这个时间复杂度是O(1)呢，它不需要遍历整个集合找到member1吗？实际这跟zset的存储机制有关，下面会说到。  

- ZRANGE key start stop [WITHSCORES]   
获取指定区间[start,stop]内的元素，时间复杂度： O(log(N)+M)，其中M为结果集基数，也就是说我们获取的元素越多，时间复杂度越高    
WITHSCORES选项可以指定返回score  
zrange key_zset 0 1  

- ZRANK key member  
返回指定member的排名，从0开始，时间复杂度: O(log(N))   
zrank key_zset member2  

- ZREM key member [member …]  
移除元素，时间复杂度: O(M*log(N))  
zrem key_zset member3  
同理支持批量操作  

- ZCOUNT key min max  
返回评分在[min,max]之间的数量，时间复杂度：O(log(N))   
zcount key_zset 0 10  

- ZCARD key   
返回zset集合的元素个数，时间复杂度O(1)  
zcard key_zset  

更多命令参考：http://redisdoc.com/sorted_set/index.html  
以上的命令可以在线运行：https://try.redis.io/#run  


## 底层数据结构    
zset的底层涉及到3个结构，一个dict字典用于存储value-score关系，这也是为什么zscore命令的时间复杂度是O(1)，因为它可以直接从字段集合获得。  
另外两个是ziplist(压缩列表)和skiplist(跳跃表)，zset最开始使用ziplist，当达到一定条件时，改变存储结构为skiplist，这个很类似于java hashmap在处理hash冲突时链表转红黑树的做法。  
至于是什么条件呢？需要同时满足如下两个条件：  
1.压缩列表个数小于128个   
2.压缩列表每个元素大小小于64字节   
这两个参数可以在redis.conf中配置  
```
zset-max-ziplist-entries 128
zset-max-ziplist-value 64
```

我们可以使用debug object key 查看key信息如下：  
![image]()  

### ziplist   
ziplist字面意思是压缩列表的意思，那么redis为什么不和java hashmap一样使用链表的呢？  
内存是redis最主要的资源，设计ziplist主要目的就是为了节省内存。普通的链表由于需要指针指向前后节点，如果本身存储的数据比较小，这个指针占用的内存可能比实际数据都大，所以ziplist需要节省这部分资源，也符合“压缩”的概念。  
那么为什么不直接使用数组呢？数组不也是连续的吗？  
这是因为ziplist的每个元素大小可以不同，这样可以实际存储的内容分配空间，同样是为了节省内存空间。  

ziplist的内存布局如下：  
![image]()  
- zlbytes：32bit无符号整数，表示ziplist占用的字节总数（包括<zlbytes>本身占用的4个字节）  
- zltail：32bit无符号整数，记录最后一个entry的偏移量，方便快速定位到最后一个entry  
- zllen：16bit无符号整数，记录entry的个数  
- entry：存储的若干个元素，可以为字节数组或者整数  
- zlend：ziplist最后一个字节，是一个结束的标记位，值固定为255  

entry的组成如下：  
![image]()  

ziplist可以通过简单的计算实现遍历。但是ziplist也有它的缺点，和数组一样，由于内存是紧凑排列，如果涉及到插入就需要扩容，这意味着需要移动数据，对性能影响很大。  
所以redis会根据zset-max-ziplist-entries和zset-max-ziplist-value动态判断，如果超过了配置，就转换数据结构，也就是skiplist。  

### skiplist   
skiplist字面意思是跳表的意思，它通过空间换时间，当zset元素比较多时，空间就不再是主要问题了，每次遍历如果耗时非常大，节省那点空间又有什么用呢。当集合元素比较多时，首要考虑的是时间。  
普通的链表查找数据需要遍历，时间复杂度是O(n)，效率太低。跳表通过分层的思想减少一些数据的比较判断，这有点类似于二分法，跳表查询的时间复杂度是O(log(N))    
如下：  
![image]()  

最底层的是完整的原始数据，通过分层每层减少节点的数据量，这里的分层就是空间的消耗，当然不是分得越多越好，需要取一个平衡。  
例如需要查询20这条数据，普通链表需要从头开始遍历6次才找到目标，而使用跳表，从顶层开始查找，可以快速定位到目标，减少遍历的次数，对于数量很大的情况下，性能会非常明显。  
![image]()  

这里是跳表基本思想，redis在此基础上做了一些优化，jdk里的ConcurrentSkipList也是跳表的实现。  


