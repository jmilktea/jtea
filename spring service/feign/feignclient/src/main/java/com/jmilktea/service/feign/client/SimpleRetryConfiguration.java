package com.jmilktea.service.feign.client;

import feign.Retryer;
import org.springframework.context.annotation.Bean;

/**
 * @author huangyb1
 * @date 2020/8/21
 */
//@Configuration
public class SimpleRetryConfiguration {

	@Bean
	public Retryer retryer() {
		return new Retryer.Default(100, 2000, 2);
	}
}
