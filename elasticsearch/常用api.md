本篇介绍在kibana上使用相关api对elasticsearch集群进行操作，相关api都可以在官网查询到[地址](https://www.elastic.co/guide/en/elasticsearch/reference/7.10/rest-apis.html)             

## \_cat api      
_cat api 与linux中的cat命令类似，允许我们对集群一些进行进行查看   

**查看集群信息**
```
GET /_cat/health 
```
该api可以查看集群的监控状况(green/yellow/red)，有多个节点，多少个数据节点，多少个分片，多少个主分片       
说明：这种写法是kibana提供的，我们可以看到实际发送的请求实际指明了执行哪个路径和方法。另外输出的结果可能有很多列，没有说明表示什么意思，可以通过api点击右侧的小工具，选择Open Document会跳转到elastic官网的api说明文档。   
![image](https://github.com/jmilktea/jmilktea/blob/master/elasticsearch/images/api-1.png)

**查看节点信息**   
```
GET /_cat/nodes   
```
该api可以查看所有节点信息，节点ip，内存使用了多少，cpu使用了多少，是否是master节点(带*号的表示master)   

**查看磁盘信息**
```
GET _cat/allocation
```
该api可以查看所有节点磁盘信息，包括磁盘总大小，已使用空间，剩余空间，该节点上分片数    
如果只想看某个节点，也可以在url后面带上节点名称，其它api也类似      
```
GET _cat/allocation/name    
```

**查看索引信息**    
```
GET _cat/indices     
```
该api可以查看集群所有索引，包括索引状态，名称，主分片数，备分配数，文档数，占用空间     
同样如果只想看某个索引，也可以在url后面带上索引名称   

**查看分片信息**
```
GET _cat/shards   
```
该api可以查看分片信息，索引有多少主分片，多少备分配，文档数，大小    

**查看段信息**
```
GET _cat/segements   
```
该api可以查看索引的段信息，每个段有多少文档数，占用多少磁盘空间，占用多少内存空间，是否可以被检索   
说明：es中段是比分配更小的单位，每个段就是一个倒排索引，segement数据不是立刻写到磁盘上的，而是会先写内存，最终在flush到磁盘，在内存的这段时间文档不能被检索到，默认是1s，所以也称es为近实时的搜索。    

## 集群api      

**查看集群信息**    
```
GET _cluster/health   
```
该api与_cat/cluster类似，可以查看集群的一些信息    

**查看节点信息**   
```
GET _nodes/name   
```
该api可以查看节点的详细信息，输出的内容非常多，例如还可以看到jvm的相关配置，从下面的图可以看到，对应的jdk版本是1.8，使用了ParNew和CMS垃圾收集器。   
这个配置可以在es config 目录下的jvm.options中配置，对于jdk10或以上版本，es会选择G1收集器，通常我们会为es分配较高的内存，此时使用G1收集器是比较合适的。   
![image](https://github.com/jmilktea/jmilktea/blob/master/elasticsearch/images/api-2.png)    

**查看节点插件**   
```
GET _nodes/plugins
```
该api可以查看节点安装了哪些插件，例如前面分词我们安装了anaylsis-hanlp，使用该api就可以查看    

## 索引api    

**创建索引**   
```
PUT /idx_test_create
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 2, 
    "refresh_interval": "30s"
  },
  "mappings": {
    "properties": {
      "title":{"type": "keyword"},
      "content":{"type": "text"},
      "comment":{"type":"text","index": false}
    }
  }
}
```
使用PUT加索引名称可以直接创建索引，settings可以配置索引的一些属性，如上指定了该索引有一个主分片2个备分片，索引刷新时间为30s，es默认是1s，这个刷新时间是指把内存数据刷到segement以提供检索的时间，对于一些不需要那么实时的可以把该值调大一点，可以提升性能。settings的更多配置[参考这里](https://www.elastic.co/guide/en/elasticsearch/reference/7.10/index-modules.html#index-modules-settings)         
mappings可以配置文档的属性，如字段类型，使用什么分词器，是否需要建立索引等。上面comment字段设置了index:false，es不会为该字段建立倒排索引，对于一些不需要检索的字段可以设置该值，有利于提升性能。    
需要注意的是，mapping内的字段是不允许修改的，但是可以新增，如下新增一个newColumn字段        
```
PUT /idx_test_create/_mappings
{
  "properties":{
    "newColumn":{"type":"text"}
  }
}
```   

**reindex**    
索引有些信息是不允许修改的，例如上面修改字段类型，或者删除字段，或者修改主分片数。当原有的索引无法满足，又无法修改时，就需要reindex重建索引。   
```
POST _reindex
{
  "source": {
    "index": "idx_test_create"
  },
  "dest": {
    "index": "idx_test_create_reindex"
  }
}
```   
如上我们创建一个新的reindex索引，执行_reindex api进行重建，重新后数据会自动迁移到新的索引。   
需要注意的是，reindex是个非常耗时的过程，当索引的文档数很大，reindex会占用很高的系统的资源和耗时，需要谨慎操作。   

**查看索引**   
```
GET idx_test_create    
```
该api可以查看我们上面创建的索引信息。   

**关闭/打开索引**
```
POST /idx_test_create/_close   
POST /idx_test_create/_open   
```
该api可以关闭/打开索引，对于一些不再需要检索的数据，关闭索引不会再占用内存空间，es只会对其元数据进行维护，还是会占用磁盘空间，但是性能有所提升，当需要时再重新open。   
对于一些日志型的场景就有用，例如7天前的日志基本不再需要检索，可以将索引close掉，有利于提升性能。    

**刷新索引**    
```
POST /idx_test_create/_refresh    
```
该api可以刷新索引，使上次写入的数据可用于检索。我们知道es是近乎实时的搜索，默认情况下有1s的延迟，写入数据后立刻查询可能会查不到，使用该api可以解决这个问题。    

**段信息**
```
GET idx_test_create/_segments    
```
该api可以查看索引的段信息，每个分片有多少段，每个段有多少文档，占用多少空间等。    
```
POST /idx_test_creata/_forcemerge 
```
该api可以手动合并段，通常es会自动合并段，当段的数量过多时，是会影响查询效率的。    

**索引模板**    
```
PUT /_template/idx_test_create_template
{
  "index_patterns":"idx_test_create*",
  "mappings": {
    "properties": {
      "title":{"type": "keyword"},
      "content":{"type": "text"},
      "comment":{"type":"text","index": false}
    }
  }  
}
```
该api创建了一个索引模板，匹配idx_test_create开头的索引名称都会使用该模板，对于有很多相同结构的索引，索引模板可以减少一些重复工作，例如日志类的模板，每天需要创建一个。    
```
PUT idx_test_create_20220125
GET idx_test_create_20220125
```
如上我们创建一个带日期的索引会自动使用索引模板   

**滚动索引**    
```
PUT /idx_test_rollover-000001
{
  "mappings": {
    "properties": {
     "content":{"type":"text"} 
    }
  },
  "aliases": {
    "idx_test_rollover": {}
  }
}
```    
上面-000001是特定的规则，例如不能改成_000001。我们创建了一个索引，并且指定了它的别名为idx_test_rollover。别名的作用是我们可以使用别名代替真正的索引名称。   
```
POST /idx_test_rollover/_rollover
{
  "conditions": {
    "max_docs":  1
  }
}
POST /idx_test_rollover/_doc
{
  "content":"test1"
}
POST /idx_test_rollover/_doc
{
  "content":"test2"
}
```
创建滚动规则，这里知道文档数大于1就符合滚动规则。   
接着写入两个文档，可以看到使用了别名但实际都写入到了idx_test_rollover-000001。   
这里的滚动是不会自动执行的，接着再执行第一个_rollover api，可以看到es创建了一个新的索引idx_test_rollover-000002，接着写入就是往000002这个索引写入了。如果文档数没有达到条件，执行该api就不会滚动创建新的索引。       

## 文档    

**添加/修改**   
```
POST idx_test_create/_doc/1
{
  "title":"elasticsearch",
  "content":"elasticsearch book is..."
}
GET idx_test_create/_doc/1   
#response
{
  "_index" : "idx_test_create",
  "_type" : "_doc",
  "_id" : "1",
  "_version" : 1,
  "_seq_no" : 0,
  "_primary_term" : 1,
  "found" : true,
  "_source" : {
    "title" : "elasticsearch",
    "content" : "elasticsearch book is..."
  }
}
```
这里我们指定了id是1，如果没有指定es会自动为我们生成一个。查询同样指定了id=1，返回的_source字段包含了其它字段信息。    
修改也是调用该api，内容改变即可。   

**删除**    
```
DELETE idx_test_create/_doc/1    
```
与mysq类似，es的删除实际是个逻辑删，删除后磁盘空间不会立刻释放，es只是把文档标记为删除，等到合适的时机才会真正删除释放空间。   

**批量操作**   
```
POST /idx_test_create/_bulk
{"index":{"_id":1}}
{"title":"elasticsearch1","content":"elasticsearch book is..."}
{"index":{"_id":2}}
{"title":"elasticsearch2","content":"elasticsearch book is..."}
{"index":{"_id":3}}
{"title":"elasticsearch3","content":"elasticsearch book is..."}
```
_bulk api有几个action,index/create/update/delete，上面我们使用了index，文档不存在会创建，存在则更新，如果使用create则是不存在就创建，存在就报错。    
批量操作在一定情况下可以较少网络开销，提升性能，es的批量操作是没有事务的，每个操作之间不会相互影响。另外需要注意的事批量的量不能过大，否则容易出现占用资源高，超时等情况。   
```    
POST /idx_test_create/_bulk
{"delete":{"_id":1}}
{"delete":{"_id":2}}
{"delete":{"_id":3}}
```
如上使用delete批量删除文档。     

**查询**    
```
GET idx_test_create/_search    
{
  "query": {
    "match_all": {}
  },
   "_source": "title"
}
```
_search api用于文档搜索，上面match_all会搜索所有文档，类似于mysql中不带条件的select。        
与select * 的问题一样，有时候我们不需要全部字段可以使用_source指定只查询哪些字段。   

**match**
```
GET /idx_test_create/_search
{
  "query": {
    "match": {
      "content": "book"
    }
  }
}
```   
这里搜索content包含book的文档，由于content是text类型，所以是模糊搜索。    
match会对搜索的内容先分词，然后在分别进行搜索，例如"book mark"会分为book和mark进行搜索，搜索条件是book或mark。   

match operator默认是or，也可以指定分词后使用and搜索，如下   
```
GET /idx_test_create/_search
{
  "query": {
    "match": {
      "content": {
        "query": "book mark",
        "operator": "and"
      }
    }
  }
}
```

**match_phrase**   
```
GET /idx_test_create/_search
{
  "query": {
    "match_phrase": {
      "content":{
        "query": "elasticsearch is"
      }
    }
  }
}
GET /idx_test_create/_search
{
  "query": {
    "match_phrase": {
      "content":{
        "query": "elasticsearch is",
        "slop": 1
      }
    }
  }
}
```
短语查询，与match类似都会分词去搜，但是短语的位置必须符合要求，例如book mark，mark必须出现在book后面，也可以指定位置，例如第一个查询“elasticsearch book is”不能被查出，因为is不是紧跟着elasticsearch，而第二个查询可以查出，因为指定了间隔为1。    

**term**    
term与match的区别是term不会对搜索的内容进行分词，如果搜索“book mark”就是搜索整个完整的词，term一般用于查询keyword字段。       

**bool**   
```
#bool
GET /idx_test_create/_search   
{
  "query": {
    "bool": {
      "must": [
        {
          "match": {
            "content": "book"
          }
        },
        {
           "term": {
            "title": {
              "value": "elasticsearch1"
            }
          }
        }
      ]
    }
  }
}
```   
bool可以指定must/must_not/should/filter 查询方式，must表示条件必须成立，should表示或的意思，相当于里面的条件是or，filter和must类似，但是filter只过滤数据，不进行评分。    
如上我们知道了bool must查询，must是由一个match和一个term查询组成，他们之间是and的关系。    
如果我们把must改成should就是or的关系，会返回更多的结果。   

**模糊查询**   
```
GET idx_test_create/_search
{
  "query": {
    "wildcard": {
      "content": {
        "value": "elastic*"
      }
    }
  }
}
```
wuldcard可以模糊搜索，如上搜索的是elastic开头的content，也可以左右模糊\*stic\*     

**范围查询**    
```
GET idx_test_range/_search
{
  "query": {
    "range": {
      "age": {
        "gte": 10,
        "lte": 20
      }
    }
  }
}
```
range可以用于范围查询，如上查询年龄大于等于10且小于等于20的。    

**分页/排序**    
```
GET /idx_test_create/_search
{
  "query": {
    "match": {
      "content": "book"
    }
  },
 "from": 0,
  "size": 3,
  "sort": [
    {
      "_id": {
        "order": "desc"
      }
    }
  ]
}
```
from/size 类似mysql中的offset/limit，与关系型数据库类似，深度分页会有性能影响，实际开发中我们应该尽量避免深度分页。    
如果没指定分页，es默认返回20条数据。   
通过sort字段可以指定排序方式，如上是order by id desc。    

**聚合查询**    
```
GET idx_test_agg/_search
{
  "query": {
    "match": {
      "name": "tom"
    }
  },
  "aggs": {
    "tom_avg": {
      "avg": {
        "field": "age"
      }
    }
  }
}
```
聚合查询类似于mysql中的求和，求平均，求最大最小值，对应的聚合类型是avg,sum,max,min。        
聚合查询使用aggs属性，聚合类型是avg，tom_avg是自定义的名称，如上查询的是名字为tom的平均年龄。   




















