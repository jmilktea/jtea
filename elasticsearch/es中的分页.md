分页查询是实际业务场景中很常见的需求，本篇我们就来介绍一下es中分页的实现方式。    
分页查询有一个典型的技术问题，就是深度分页问题，这个问题在mysql中也很常见，当我们查询较前面的页时，查询速度很快，但随着offset越来越大时，分页的效率越来越低，最终会变成一个占用资源高的慢查询。  
在mysql中如果有此类需求，我们会考虑能否用id偏移，每次只取第一页的数据，例如需要遍历某张表某个状态的全部数据，群发短信，那我们就可以使用id偏移，这样就不会有offset越来越大的问题。实际深度分页并没有完美的解决方案，但它在实际生活中的需求也不是特别强烈，用户一般只会看前面几页的数据，很少有用户会一直往后面翻，所以一般在业务上也会有折中，设计上就不允许用户进行深度分页。   
es中的分页和mysql中的分页是很像的，实现思路也类似，本质他们都是一种数据存储介质。    

我们简单准备一下测试数据
```
PUT /idx_test_pagination
{
  "mappings": {
    "properties": {
      "id":{"type": "long"},
      "author":{"type": "keyword"},
      "age":{"type": "short"}
    }
  }
}
POST /idx_test_pagination/_bulk
{"index":{"_id":1}}
{"author":"wang","age":28}
{"index":{"_id":2}}
{"author":"jack","age":20}
{"index":{"_id":3}}
{"author":"lili","age":30}
{"index":{"_id":4}}
{"author":"lucy","age":35}
{"index":{"_id":5}}
{"author":"tom","age":20}
```

## from + size    
在前面api介绍我们也介绍了from + size实现的分页，语法如下：   
```
GET /idx_test_pagination/_search
{
  "query": {
    "match_all": {}
  }, 
  "from": 2,
  "size": 2
}
```
这样我们会取到id 3,4的两条数据。    
from + size 跟mysql中limit offset 是一样的，也同样存在深度分页性能低下问题，本质上他们都是先选出数据再筛选出结果，例如 from 10000 size 2，实际上会先从es中查询出10002条数据，再过滤得到2条结果数据。  
所以from的值越大，要查询的数据也就越大，占用的资源也就越高，会影响性能，所以es对此做了限制，默认情况下分页要查询的数据不能超过10000条，如果超过就会报“Result window is too large”的错误。  
例如我们使用 from 10000 size 2，就会报错，这个是通过index.max_result_window参数配置的，也可以通过如下方式修改，但不建议这么做    
```
PUT /idx_test_pagination/_settings
{
  "index":{
    "max_result_window":20000
  }
}
```

我们知道es是有分片的，那分页查询的过程是怎么样的呢？步骤如下：   
1. 客户端发起搜索请求，请求会达到coordinating node，也就是协调节点，协调节点会根据索引分片信息，将请求路由到数据节点。    
2. 数据节点收到请求后，会在内存建立一个from + size 大小的优先级队列，并执行搜索请求结果保存到这个队列，需要注意的是，这里不会查询整个文档的内容，主要是文档id，这样是为了保证性能。      
3. 各个数据节点完成搜索会，将结果返回到协调节点。   
4. 协调节点会建立一个 n * (from + size) 优先级队列，n是分片的数量，用来保存各个数据节点返回的结果。    
5. 协调节点对数据进行排序过滤，筛选出size条结果。  
6. 根据筛选的结果，再次发出请求到各个数据节点，请求文档内容，返回给客户端。    

可以看到当分页深度越大，占用的资源就会越高，不仅影响数据节点，还影响协调节点，因为它需要保存各个节点的查询结果，再排序过滤得到最终结果。    
这种分片上的分页，跟mysql中分库分表的分页查询是一个道理，例如我们使用sharding-jdbc做客户端分库分表的分页查询，也会在各个库查询结果后，再在服务内存汇总进行过滤得到结果。    

## search after    
search after是es5 引入的一种分页机制，实际上就是我们开头说到mysql中使用id偏移的机制，核心就是将本次查询结果做为条件带入下一次查询，这样就可以在查询时过滤掉，只取少量数据，同样在coordinating节点也只需要对少量数据进行处理，大幅提升性能。   
```
GET /idx_test_pagination/_search
{
  "query": {
    "match_all": {}
  }, 
  "size": 2, 
  "sort": [
    {
      "age": {
        "order": "desc"
      },
      "_id":{
        "order": "asc"
      }
    }
  ]
}
```
如上，我们按order by age desc,_id 查询2条数据，查询结果会有sort字段，如：
```
      {
        "_index" : "idx_test_pagination",
        "_type" : "_doc",
        "_id" : "3",
        "_source" : {
          "author" : "lili",
          "age" : 30
        },
        "sort" : [
          30,
          "3"
        ]
      }
```
接着我们可以把sort作为search after传入下一次查询，如：
```
GET /idx_test_pagination/_search
{
  "query": {
    "match_all": {}
  }, 
  "size": 2, 
  "search_after":[30,3],
  "sort": [
    {
      "age": {
        "order": "desc"
      },
      "_id":{
        "order": "asc"
      }
    }
  ]
}
```
search after并不适合跨页或者指定页数的查询，但对于那种每次只能查下一页的场景特别有用，例如在app上的滑动分页，每次都是拉到底部加载下一页，这中没有跨页的查询使用search after相比from + size可以大大提升性能。    

PIT：point in time，时间点，es7.10后开始提供，本质上是一个视图，和下面要介绍的scroll查询很像。    
由于在查询过程中数据可能会变动，我们可以指定一个时间点视图，后续查询都基于这个它进行，不会被数据变动所影响，避免排序受影响。   
```
POST /idx_test_pagination/_pit?keep_alive=1m
-- 返回id
{
  "id" : "w62xAwETaWR4X3Rlc3RfcGFnaW5hdGlvbhZReXIxV3FrblFVNm45WTNObFpXV1F3ABZMaFE2SDhheVRNT1hkcUd3TGNlRUJRAAAAAAAAABClFm55aFpweDVXUWhlUDA4UkNxbEVQOVEBFlF5cjFXcWtuUVU2bjlZM05sWldXUXcAAA=="
}
```
基于pit id查询，如果过期后查询会报错，期间如果对数据的变动，不会影响到视图。   
```
POST /_search
{
  "query": {
    "match_all": {}
  }, 
   "pit": {
    "id":"w62xAwETaWR4X3Rlc3RfcGFnaW5hdGlvbhZReXIxV3FrblFVNm45WTNObFpXV1F3ABZMaFE2SDhheVRNT1hkcUd3TGNlRUJRAAAAAAAAABClFm55aFpweDVXUWhlUDA4UkNxbEVQOVEBFlF5cjFXcWtuUVU2bjlZM05sWldXUXcAAA=="
  }
}
```   
**PIT原理**    
es是如何帮我们创建这个视图的呢？最简单的理解就是将所有数据拷贝一份，用上面的id标记，很明显这种方式不可行，在索引文档很多的情况下，是无法实现的。   
实际上es是通过阻止段合并过程中删除文档来实现的，修改本身也是先删除再插入。为了提升检索效率，es会定时对段进行合并，将较小的段合并为大段，并清理掉被标记为删除的文档，当我们创建PIT，在这段时间内，段合并后就不会删除较小的段，因为它们可能还被使用，es在查询的时候，基于时间点的查询就可以知道要查询文档哪个时间点的数据，所以PIT keep alive的时间不宜过长，段一直没被删除会占用系统资源。   

## scroll
scroll查询相当于是生成一个快照，并且可以指定快照的缓存时间，类似于游标，通过一个scroll id去遍历数据。我们先看下怎么使用   
```
GET /idx_test_pagination/_search?scroll=1m
{
  "query": {
    "match_all": {}
  },
  "size": 2
}
```
这里我们加了一个scroll=1m的参数，查询结果会返回一个_scroll_id字段，可以理解为游标的位置    
```
  "_scroll_id" : "FGluY2x1ZGVfY29udGV4dF91dWlkDXF1ZXJ5QW5kRmV0Y2gBFm55aFpweDVXUWhlUDA4UkNxbEVQOVEAAAAAAAANFRZMaFE2SDhheVRNT1hkcUd3TGNlRUJR",
```
接着就可以拿着这个scroll id继续查询，需要注意的是，继续查询只需要scroll id，不需要我们前面的query条件了，因为已经创建了一个基于当前查询条件的快照，后面的查询都是基于这个查询条件进行。
这样每次查询都传返回新的scroll id，再进行下一次查询，就可以遍历完所有数据。    
```
POST /_search/scroll
{
    "scroll":"1m",
    "scroll_id":"FGluY2x1ZGVfY29udGV4dF91dWlkDXF1ZXJ5QW5kRmV0Y2gBFm55aFpweDVXUWhlUDA4UkNxbEVQOVEAAAAAAAANehZMaFE2SDhheVRNT1hkcUd3TGNlRUJR"
}
```
scroll查询是基于快照和缓存的，基于快照，意味着在快照生成后，数据的新增、删除不会被感知到，例如在查询过程中新加一条数据，是不会被查询出来的。另外我们上面设置了缓存时间为1m，每次查询设置1m都会将缓存时间延长1分钟，如果超过这个时间再去查询，会报错，因为快照已经被es清除。同时缓存的时间不能设置过长，会长时间占用es的资源。     
scroll查询适用于一些数据的导出，例如报表，对数据的实时性要求不是特别高的场景。此外快照的建立是非常占用资源的，它会把所有符合条件的文档id都查询出来缓存，虽然不会缓存整个文档，但如果量特别大，依然会很占用内存资源，所以它只适合一些后台的数据导出类似的需求。此外，在es7开始，官方建议使用search after PIT的方式来代替scroll查询。       
```
We no longer recommend using the scroll API for deep pagination. If you need to preserve the index state while paging through more than 10,000 hits, use the search_after parameter with a point in time (PIT)
```   

参考：
[es paginate-search-results](https://www.elastic.co/guide/en/elasticsearch/reference/7.10/paginate-search-results.html)    
[实战解决ElasticSearch深度分页问题](https://www.shouxicto.com/article/989.html)   









