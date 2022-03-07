es的核心概念我们在[es核心概念](https://github.com/jmilktea/jmilktea/blob/master/elasticsearch/elasticsearch%E6%A0%B8%E5%BF%83%E6%A6%82%E5%BF%B5.md)这篇已经有一个初步的认识    
这次我们学习一下es的写入原理，看看一个文档写入的完整过程    

![image](https://github.com/jmilktea/jmilktea/blob/master/elasticsearch/images/es-write1.png)    

如上图所示，节点2接收到客户端的写入请求，由于它是保存的是副本分片(只能处理读请求)，所以需要将请求转发给主分片的节点1。在es集群中，每个节点都知道主分片节点的信息。    
上图我们只有一个主分片，在数据量比较大的时候，通常我们会创建多个主分片，它们分布在不同的节点上，具体是指定参数number_of_shards。    
那么怎么知道将请求转发到哪个节点上呢？es是通过 shard = hash(routing) % number_of_shards 计算得出，routing 是要计算hash的字段，我们可以手动指定，如果没有指定用的就是id字段，通过routing计算出hash值后取余主分片的数量，就可以得出文档所在的节点。        

**主分片数不可修改**   
我们知道es中主分片数一旦确定就不可修改，这是因为如果修改上面shard的计算结果就会改变，会导致路由结果改变，就不知道已经在哪个主节点写入过了。    

**index buffer**    
当请求达到主分片后，es首先会写入**index buffer**缓冲区域，此时文档还是不能被检索到的。    
index buffer是个全局参数，意味着它会被节点上的其它分片使用，不单单是某个索引。它相关的配置参数有3个，如下：   
indices.memory.index_buffer_size：index buffer的大小，默认是占es堆内存的10%，例如我们给es分配10g内存，那么index buffer就要占用1g。   
indices.memory.min_index_buffer_size：index buffer最小大小，默认是48m，如果10%太多，可以调低这个参数。         
indices.memory.max_index_buffer_size：index buffer最大大小，index_buffer_size指定的是百分比，这个可以指定具体数值。    

**refresh**    
文档在index buffer中无法被检索，默认情况下es每一秒会触发一个refresh的动作，将index buffer中的内容生成一个segment，segment是lucene中索引分配的段，实际就是一个倒排索引文件，可用于搜索。refresh后会清空index buffer，用于存储后续的数据。    
所以执行完refresh后，文档就已经进入lucene内部，可以被检索，这也是为什么es被称为近乎实时搜索的原因，它的文档不是立即可以被检索的，这个可以通过设置索引的refresh_interval参数指定refresh的时间，默认是1s，对于一些实时性要求不高的场景，例如日志，可以适当延长这个时间，提升性能。    

**那么es为什么要设计index buffer，隔1s再写入到lucene呢？**    
这个当然是处于性能的考虑，我们知道每个lucene segment就是一个倒排索引，写入倒排索引需要经过分词、建立索引等一系列耗时的操作，如果每次都直接写入，那么写入性势必会下降。    
另外，与我们平时操作文件api类似，我们都会先写到一个buffer中，达到一定条件后再一批写入到磁盘文件，可以提升性能，index buffer也是类似原理，单个请求转化为批量请求，性能会提升。    

**如何做到实时搜索呢？**    
实际场景中我们可能写入后就想立刻查询到，怎么实现呢？   
1.写入后同时缓存一份在redis，查询的时候先查询redis，查不到再查es。缓存的时间可以很短，根据refresh_interval决定，很短的时间也不会占用很多redis内存。   
2.手动触发refresh，对于需要立刻查询的场景，我们也可以手动调用api触发refresh。        
```
POST /myindex/_refresh
```

**一个疑问？**    
对于refresh，生成segment，网上很多文章都喜欢将它描述为：这个过程不会立刻写入磁盘，而是先进入os cache（file system cache）。看到这个os cache我非常疑惑，怎么进入到os cache的？无论是es还是lucene都是使用java开发，java哪个api可以操作os cache？也没有找到相关文档说明。     
当es执行refresh时，实际对应的是lucene的**flush**操作，lucene不是与es公用一块jvm内存，lucene使用的是堆外内存，es refresh生成segment是存储在这里，并没有持久化，网上文章说的os cache指的就是这块区域。es推荐将机器的一半内存留给lucene，[参考地址](https://www.elastic.co/guide/cn/elasticsearch/guide/current/heap-sizing.html)        

**段合并**    
es refresh每秒就会生成一个segment，时间久了segment的数量就会非常多，查询的时候需要遍历所有的segment再将结果汇总，segment过多会影响查询性能。       
所以es会自动触发segment merge，将小的segment合并成大的segment，在合并的同时会删掉那些标记为删除的文档。    
也可以配置相关参数：   
index.merge.policy.fool_segment: 2mb，当段大小达到2m自动触发合并   
index.merge.max_merge_at_once: 10，每次合并最多合并多少个段，默认是10     
index.merge.max_merge_at_once_explicit：30，显示触发段合并时最多合并多少个端，我们可以收到调用api POST /myindex/_forcemerge 触发段合并     
index.merge.max_merged_segment: 5gb，段最大大小，达到这个大小就不再合并了，默认是5g     
index.merge.scheduler.max_thread_count: 合并时最多使用的线程数，默认是 Math.max(1, Math.min(4, node.processors / 2))     

段合并的思想类似于redis的AOF重写，本质上都是将小和零碎的东西合并成一个大的来管理。AOF是每次将命令追加保存起来，时间久了占用的空间就会大，且执行起来比较久，AOF重写的目的就是将命令合并，减少占用的空间。例如：    
```
LPUSH mylist "hello"     
LPUSH mylist "tom"    
LPUSH mylist "jack"
-- aof rewrite 合成一条命令   
LPUSH mylist "hello" "tom" "jack"    
```

**flush**     
上面说的无论是index buffer还是lucene segment，都还是在内存空间上，当发生机器重启，内容就会丢失，所以得有一套机制来持久化，这个就是flush。   
es中的flush对应lucene中的commit，用于将内存中的段持久化到磁盘，这样就永久保存起来，不会丢失了。    
flush操作会先触发refresh，将index buffer的内容清空，然后触发段合并，再将内存中的段写入到磁盘，同时写commit point，可以看到flush是一个比refresh重得多的操作。    
commit point直译过来是提交点，lucene commit完成后写commit point，记录已经持久化到磁盘的segment信息。    

那么什么时候会触发flush呢？    
当translog达到一定大小的时候，默认是512m，就会触发flush。    
老版本还有一个配置flush_threshold_period，默认是30分钟，就会触发flush，但是我在es7.10没有看到改参数了。    

上面我们提到文档有一段时间是在内存中的，如果在flush之前，机器重启了，数据就会丢失。这个问题怎么解决呢？答案就是translog。    

**translog**     
es的持久化机制采用了和关系型数据库类似的持久化机制，称为“WAL”(write ahead log)。   
WAL的核心思想是，在数据写入时，不是直接写磁盘，而是写内存和日志，然后在一定的时间将内存中的数据再持久化到磁盘，如果期间发生重启，则通过日志找回数据。    
由于磁盘随机读写的低效性，如果每次写入都直接写磁盘效率会很低，写内存就快很多了，同时将本次操作写一份到日志文件，日志写是顺序写，速度也很快，以此来提高写入性能。   
例如mysql会写redo log实现数据的持久化，当发生故障时，可以从redo log找回还没持久化的内容。关于顺序写的性能，我们在[这里](https://github.com/jmilktea/jmilktea/blob/master/linux/%E9%A1%BA%E5%BA%8FIO.md)讨论过。   

当执行flush后，translog会被删除，translog的命名格式是translog-N，例如删除translog-0后，下一个translog-1。      
前面我们提到commit point会记录segment的信息，那么当发生重启的时候，es就可以通过commit point知道哪些segment是已经持久化的，同时通过translog进行重放，将未持久化的数据重新写到内存。     

translog相关配置参数    
index.translog.flush_threshold_size：512mb，默认是512m，超过这个大小就会触发flush。       
index.translog.durability: request，默认是每次请求都持久化translog，这样可以保证数据不丢失。还可以选择async，异步写入translog，这样写入性能会更高，但有丢失数据的风险。        
index.translog.sync_interval：5s，表示异步持久化translog的时间，如果期间发生重启，会有这个时间内丢失数据的风险。    

flush过程     
![image](https://github.com/jmilktea/jmilktea/blob/master/elasticsearch/images/es-write2.png)   

**参考**    
[es配置](https://www.elastic.co/guide/en/elasticsearch/reference/7.10/index.html)    
[refresh & flush](https://qbox.io/blog/refresh-flush-operations-elasticsearch-guide)   
[es写入原理](https://www.toutiao.com/i7021046071157719589/?tt_from=weixin&utm_campaign=client_share&wxshare_count=1&timestamp=1646577075&app=news_article&utm_source=weixin&utm_medium=toutiao_android&use_new_style=1&req_id=2022030622311501013103409901AF9732&share_token=8067c3fa-af7c-41c3-af96-3308d18fd38c&group_id=7021046071157719589)     
