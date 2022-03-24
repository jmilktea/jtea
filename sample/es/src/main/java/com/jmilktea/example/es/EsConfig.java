package com.jmilktea.example.es;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangyb1
 * @date 2022/3/23
 */
@Configuration
public class EsConfig {

	@Bean
	public RestHighLevelClient restHighLevelClient() {
		RestHighLevelClient restHighLevelClient = new RestHighLevelClient(
				RestClient.builder(
						new HttpHost("192.168.56.102", 9200, "http")
				));
		return restHighLevelClient;
	}
}
