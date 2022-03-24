在[常用api篇]()我们介绍一些基础的查询，本篇我们已springboot为例来使用java api来访问es，实现各种操作，这些例子都和常用api篇是对应的。    

es本身提供了两种rest api client用于交互，分别是：   
Java Low Level REST Client：一种“低级”客户端，它封装了基础的功能并发一些请求响应的解析留给用户自己去实现。    
Java High Level REST Client：一种高级客户端，做了较好的封装，可以用面向对象的编程方式实现与es交互，通常我们使用这种方式。    

熟悉spring的朋友都知道，spring有一套spring data机制，用于抽象高层接口，在对各种数据源提供实现，例如spring-data-redis,spring-data-jpa，同样有spring-data-elasticsearch。    
使用spring-data-elasticsearch会非常简单，spring提供了很好的封装，使用方便，7.0以前的版本，spring-data-elasticsearch是基于es TransportClient的封装(TCP的方式，访问的是9300端口)，后面这种访问方式被es废弃了，7.0以后的版本spring也是基于es提供的高级客户端实现。在使用的时候需要注意版本需要和es server保持一致，但有一个问题是spring-data-elasticsearch并不能紧跟es的节奏发布，例如现在es已经8.0了，最新的spring-data-elasticsearch还停留在7.x版本，所以如果能确保使用的es版本不会改变，可以使用spring提供的方式，否则更建议使用es官方提供的高级客户端。    

**准备**   
导包，注意版本
```
        <dependency>
            <groupId>org.elasticsearch</groupId>
            <artifactId>elasticsearch</artifactId>
            <version>7.10.0</version>
        </dependency>
        <dependency>
            <groupId>org.elasticsearch.client</groupId>
            <artifactId>elasticsearch-rest-client</artifactId>
            <version>7.10.0</version>
        </dependency>
        <dependency>
            <groupId>org.elasticsearch.client</groupId>
            <artifactId>elasticsearch-rest-high-level-client</artifactId>
            <version>7.10.0</version>
        </dependency>
```    
创建RestHighLevelClient bean  
```
	@Bean
	public RestHighLevelClient restHighLevelClient() {
		RestHighLevelClient restHighLevelClient = new RestHighLevelClient(
				RestClient.builder(
						new HttpHost("ip", 9200, "http")
				));
		return restHighLevelClient;
	}
```
准备数据，索引包含了id,作者，年龄，标题，内容字段，如下：    
```
PUT /idx_test_java_api
{
  "mappings": {
    "properties": {
      "id":{"type": "long"},
      "author":{"type": "keyword"},
      "age":{"type": "short"},
      "title":{"type": "text"},
      "content":{"type": "text"}
    }
  }
}
```
如下的api用到几个关键类和api关系如下：  
![image](1)

**新增数据**
```
	@Test
	public void testAdd() throws IOException {
		Author author = new Author();
		author.setAuthor("tom");
		author.setAge(18);
		author.setTitle("elasticsearch");
		author.setContent("hello elasticsearch");

		IndexRequest indexRequest = new IndexRequest(INDEX_NAME);
		indexRequest.id("1");
		indexRequest.source(objectMapper.writeValueAsString(author), XContentType.JSON);
		IndexResponse response = client.index(indexRequest, RequestOptions.DEFAULT);
		System.out.println(response.status()); //OK
	}
```
单条新增，这相当于
```
POST /idx_test_java_api/_doc/1
{"author":"tom","age":18,"title":"elasticsearch","content":"hello elasticsearch"}
```
批量新增
```
	@Test
	public void testBatchAdd() throws IOException {
		Author author1 = new Author();
		author1.setAuthor("jack");
		author1.setAge(20);
		author1.setTitle("mysql");
		author1.setContent("hello mysql");
		IndexRequest indexRequest1 = new IndexRequest(INDEX_NAME);
		indexRequest1.id("2");
		indexRequest1.source(objectMapper.writeValueAsString(author1), XContentType.JSON);

		Author author2 = new Author();
		author2.setAuthor("lili");
		author2.setAge(30);
		author2.setTitle("redis");
		author2.setContent("hello redis");
		IndexRequest indexRequest2 = new IndexRequest(INDEX_NAME);
		indexRequest2.id("3");
		indexRequest2.source(objectMapper.writeValueAsString(author2), XContentType.JSON);

		BulkRequest bulkRequest = new BulkRequest();
		bulkRequest.add(indexRequest1);
		bulkRequest.add(indexRequest2);

		BulkResponse bulk = client.bulk(bulkRequest, RequestOptions.DEFAULT);
		System.out.println(bulk.status()); //OK
	}
```
这相当于使用_bulk api批量添加
```
POST /idx_test_java_api/_bulk
{"index":{"_id":2}}
{"author":"jack","age":20,"title":"mysql","content":"hello mysql"}
{"index":{"_id":3}}
{"author":"lili","age":30,"title":"redis","content":"hello redis"}
```
添加成功我们可查看一下数据已经正常保存    
```
GET /idx_test_java_api/_search
{
  "query": {
    "match_all": {}
  }
}
```

## 查询    
**match**    
```
	@Test
	public void testMatch() throws IOException {
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(QueryBuilders.matchQuery("content", "hello elasticsearch"));

		SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
		searchRequest.source(searchSourceBuilder);
		SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
		System.out.println("search result total:" + response.getHits().getTotalHits().value);
		Arrays.stream(response.getHits().getHits()).forEach(s -> {
			System.out.println("result:" + s.getSourceAsString());
		});
	}
```
由于match是会进行分词的，所以上面会把content包含"hello"或"elasticsearch"的都查出来。等同于
```
GET /idx_test_java_api/_search
{
  "query": {
    "match": {
      "content": "hello"
    }
  }
}
```

**term**    
```
	@Test
	public void testTerm() throws IOException {
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(QueryBuilders.termQuery("title", "elasticsearch"));
		
		SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
		searchRequest.source(searchSourceBuilder);
		SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
		System.out.println("search result total:" + response.getHits().getTotalHits().value);
		Arrays.stream(response.getHits().getHits()).forEach(s -> {
			System.out.println("result:" + s.getSourceAsString());
		});
	}
```
由于term query不会分词，所以如果我们还是像上面一样查询content:hello elasticsearch 就会查不到数据。等同于    
```
GET /idx_test_java_api/_search
{
  "query": {
    "term": {
      "title": "elasticsearch"
    }
  }
}
```
同时我们注意到上面的查询结果都包含score，也就是es会对结果进行一个相关性评分，这需要消耗性能，如果我们不需就指定不进行评分。  
使用的是constantScoreQuery查询     
```
searchSourceBuilder.query(QueryBuilders.constantScoreQuery(QueryBuilders.termQuery("title", "elasticsearch")));
``` 

**bool**   
```
	@Test
	public void testBool() throws IOException {
		SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(
				QueryBuilders.boolQuery()
						.must(QueryBuilders.termQuery("title", "elasticsearch"))
						.must(QueryBuilders.matchQuery("content", "lucene"))
		);
		searchRequest.source(searchSourceBuilder);
		SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
		System.out.println("search result total:" + response.getHits().getTotalHits().value);
		Arrays.stream(response.getHits().getHits()).forEach(s -> {
			System.out.println("result:" + s.getSourceAsString());
		});
	}
```
bool查询使用QueryBuilders.boolQuery方法，如上查询的是title是elasticsearch，并且content包含lucene的文档。等同于 
```
GET /idx_test_java_api/_search   
{
  "query": {
    "bool": {
      "must": [       
        {
           "term": {
            "title": {
              "value": "elasticsearch"
            }
          }
        },
         {
          "match": {
            "content": "lucene"
          }
        }
      ]
    }
  }
}
```

**范围查询**   
```
	@Test
	public void testRange() throws IOException {
		SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(
				QueryBuilders.rangeQuery("age").gte(20).lte(30)
		);
		searchRequest.source(searchSourceBuilder);
		SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
		System.out.println("search result total:" + response.getHits().getTotalHits().value);
		Arrays.stream(response.getHits().getHits()).forEach(s -> {
			System.out.println("result:" + s.getSourceAsString());
		});
	}
```
如上我们查询的是年龄在20-30之间的所有文档，等同于   
GET /idx_test_java_api/_search
{
  "query": {
    "range": {
      "age": {
        "gte": 20,
        "lte": 30
      }
    }
  }
}

**分页排序**   
```
	@Test
	public void testPageAndSort() throws IOException {
		SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(
				QueryBuilders.rangeQuery("age").gte(20).lte(30)
		);
		searchSourceBuilder.sort("age", SortOrder.DESC);
		searchSourceBuilder.from(0);
		searchSourceBuilder.size(3);
		searchRequest.source(searchSourceBuilder);
		SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
		System.out.println("search result total:" + response.getHits().getTotalHits().value);
		Arrays.stream(response.getHits().getHits()).forEach(s -> {
			System.out.println("result:" + s.getSourceAsString());
		});
	}
```
如上我们查询年龄范围在20-30之间的文档，并且按照年龄降序，取前3条，等同于
```
GET /idx_test_java_api/_search
{
  "query": {
    "range": {
      "age": {
        "gte": 20,
        "lte": 30
      }
    }
  },
  "from": 0,
  "size": 3,
  "sort": [
    {
      "age": {
        "order": "desc"
      }
    }
  ]
}
```

**聚合查询**   
```
	@Test
	public void testAggAvg() throws IOException {
		SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(QueryBuilders.termQuery("title", "elasticsearch"));
		searchSourceBuilder.aggregation(
				AggregationBuilders.avg("avg_age").field("age")
		);
		searchRequest.source(searchSourceBuilder);
		SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
		System.out.println("avg age is:" + ((ParsedAvg) response.getAggregations().asMap().get("avg_age")).getValue());
		Arrays.stream(response.getHits().getHits()).forEach(s -> {
			System.out.println("result:" + s.getSourceAsString());
		});
	}
```
如上我们查询标题是elasticsearch，作者的平均年龄，等同于
```
GET /idx_test_java_api/_search
{
  "query": {
    "term": {
      "title": "elasticsearch"
    }
  },
  "aggs": {
    "avg_age": {
      "avg": {
        "field": "age"
      }
    }
  }
}
```






