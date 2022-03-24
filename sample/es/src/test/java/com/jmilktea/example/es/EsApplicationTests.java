package com.jmilktea.example.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.ParsedAvg;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.util.Arrays;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = EsApplication.class)
public class EsApplicationTests {

	@Autowired
	private RestHighLevelClient client;

	private final static String INDEX_NAME = "idx_test_java_api";

	private ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void testAdd() throws IOException {
		Author author = new Author();
		author.setAuthor("me");
		author.setAge(22);
		author.setTitle("test");
		author.setContent("my test2");

		IndexRequest indexRequest = new IndexRequest(INDEX_NAME);
		indexRequest.id("5");
		indexRequest.source(objectMapper.writeValueAsString(author), XContentType.JSON);
		IndexResponse response = client.index(indexRequest, RequestOptions.DEFAULT);
		System.out.println(response.status());
	}

	@Test
	public void testBatchAdd() throws IOException {
		Author author1 = new Author();
		author1.setAuthor("me6");
		author1.setAge(22);
		author1.setTitle("test");
		author1.setContent("my test");
		IndexRequest indexRequest1 = new IndexRequest(INDEX_NAME);
		indexRequest1.id("6");
		indexRequest1.source(objectMapper.writeValueAsString(author1), XContentType.JSON);

		Author author2 = new Author();
		author2.setAuthor("me7");
		author2.setAge(22);
		author2.setTitle("test");
		author2.setContent("my test");
		IndexRequest indexRequest2 = new IndexRequest(INDEX_NAME);
		indexRequest2.id("7");
		indexRequest2.source(objectMapper.writeValueAsString(author2), XContentType.JSON);

		BulkRequest bulkRequest = new BulkRequest();
		bulkRequest.add(indexRequest1);
		bulkRequest.add(indexRequest2);

		BulkResponse bulk = client.bulk(bulkRequest, RequestOptions.DEFAULT);
		System.out.println(bulk.status());
	}

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

	@Test
	public void testNoScore() throws IOException {
		SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(QueryBuilders.constantScoreQuery(QueryBuilders.termQuery("title", "elasticsearch")));
		searchRequest.source(searchSourceBuilder);
		SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
		System.out.println("search result total:" + response.getHits().getTotalHits().value);
		Arrays.stream(response.getHits().getHits()).forEach(s -> {
			System.out.println("result:" + s.getSourceAsString());
		});
	}

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

	@Test
	public void testAggAvg() throws IOException {
		SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(QueryBuilders.termQuery("title", "elasticsearch"));
		searchSourceBuilder.aggregation(
				AggregationBuilders.max("avg_age").field("age")
		);
		searchRequest.source(searchSourceBuilder);
		SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
		System.out.println("avg age is:" + ((ParsedAvg) response.getAggregations().asMap().get("avg_age")).getValue());
		Arrays.stream(response.getHits().getHits()).forEach(s -> {
			System.out.println("result:" + s.getSourceAsString());
		});
	}

}
