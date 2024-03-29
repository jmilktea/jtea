本篇结束es中的一些概念，明白这些概念和帮助我们理解整个es的架构设计，也是后续学习es的重要基础     

## 节点(node)     
一个节点就是一个es实例，也是一个java进程，一个机器上可以部署多个节点。节点又分为多种类型。  

**主节点(master node)**     
默认node.master:true表示该节点可以成为主节点。   
主节点由选举产生，负责集群索引的创建和删除，决定分片的分配等，是集群的首脑。   

**数据节点(data node)**   
默认node.data:true表示该节点是数据节点。   
数据节点负责存储数据和索引，对文档的增删查改，数据节点通常要求比较高的硬件配置。   
一个节点可以同时充当主节点和数据节点。   

**协调节点**   
当node.master:false和node.data:false同时为false时，表示该节点既不成为主节点也不存储和索引文档，那么它就是一个协调节点。   
协调节点只起到一个转发请求的作用，当集群节点比较多的时候，协调节点可以起到均衡的作用。    

默认情况下node.master:true，node.data:true，节点既可以成为主节点也是数据节点，这对于一个小型的es集群是完全可以接受的，只要当集群节点足够多的时候才需要考虑分离主节点和数据节点，甚至添加协调节点来负载均衡，否则太多的节点会增加运维成本。      

## 集群    
单节点始终存在存储和性能瓶颈，为了能存储和索引更多数据，保证高可用，就会部署多个es实例，组成一个集群。    
相同cluster.name的节点会组成一个集群，默认为“elasticsearch”，单个节点也是一个集群。   

**集群状态**   
es为集群定义了3种健康状态   
green: 所有主备分片都正常，此时系统处于健康状态   
yellow: 所有主分片都正常，有备分片不正常，此时系统依然能正常对外服务，但有备分片不正常处于亚健康状态    
red: 有主分片不正常，此时路由到此节点的请求就会失败，需要及时处理     

关于什么是主、备分片接下来说到    

## 文档(document)    
文档可以类比mysql中的行，es中数据以文档形式存在索引中，每个文档都有一个唯一id，如果没有指定id会自动生成一个    
文档使用json格式表示，例如产品文档：   
```
POST /test_idx/_doc
{
    "productName":"iphone",
    "productPrice":"8000"
}
```

## 字段(field)    
一个文档相当于mysql中的一行，那么字段就相当于列。在mysql中我们可以定义字段的类型，长度等，在es中也可以对字段进行设置，这个设置是在index中描述的。      

## 类型(type)    
一个索引可以有多个type，可以类比mysql中一个数据库可以有多张表，在新版的es中一个index只会有一个_doc类型，type概念会被废弃，知道一下即可。   

## index(索引)   
索引可以类比关系型数据库中的数据库概念，文档存在在索引中。它定义了一系列的元数据(描述)，例如文档的主分片数，备分片数，字段描述等。    

**mapping**    
mapping就是多文档字段的定义，例如定义字段的名称，类型，字段是否需要建立索引，使用哪个分词器等。    
```
PUT /product_index/
{
  "settings": {
    "index": {
        "number_of_shards" : "1",
        "number_of_replicas" : "1"
    }
  },
  "mappings": {
    "properties": {
      "productName" : {
        "type": "keyword"        
      },
      "productPrice" : {
        "type": "double"        
      },
    }
  }
}
```

## 主分片(primary shard)/备分片(replica shard)    
一个索引的文档数量可能是非常庞大的，在mysql中当一个表的数据量达到千万级别，我们就会考虑分库分表，来降低单表的数据量。这个在es中就是分片，分片就是将文档数据拆分分布在不同节点上，有了分片就可以横向扩展，支持存储更多的数据。   
主分片负责对文档进行写入，然后es会把数据同步到备分片上，一个主分片可以有多个备分片，主备分片都可以被读取，备分片除了可以起到备份数据的作用，分担读取请求还可以提升系统的吞吐量。   
相同索引的主备分片不会被分配到同一个节点上，否则一旦出现就都不可用。   

## 段(segment)   
一个索引可以被拆成多个分片，在每个分片上，会进一步拆分成更小的段。   
每次对文档的搜索实际就是对段的搜索，然后对结果进行汇总合并。   
每个段就是一个倒排索引，倒排索引是es中核心数据结构，与B+树类似，是为了加快搜索速度，后面我们专门介绍。    

上面所介绍的index、document、segment实际上都是lucene的概念，es在lucene上层做封装。   

## DSL(Domain Specific Language)    
这个是es中的查询语法规则，可以类比sql，dsl是通过json结构来描述查询。
查询语法不需要特别记忆，写多几次，用到的时候查一下即可，通常我们使用java对接es都有封装好的api。    
示例，查询所有产品：   
```
GET product_index/_doc/_search
{   
  "query": {
    "match_all": {}
  }
}
```

## 总结   
介绍了es中的一些基本概念，方便后续的学习。   
我们用一张图对上面的概念进行直观的总结，也与关系型数据库做一个比较        

![image](https://github.com/jmilktea/jmilktea/blob/master/elasticsearch/images/es-base-1.png)   

![image](https://github.com/jmilktea/jmilktea/blob/master/elasticsearch/images/es-base-2.png)   
 

















