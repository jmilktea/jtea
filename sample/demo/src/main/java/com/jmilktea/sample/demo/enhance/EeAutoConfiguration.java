package com.jmilktea.sample.demo.enhance;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * @author suxf
 * @create 2021/12/27 14:56
 * @since
 **/
@Configuration
public class EeAutoConfiguration {

	@Bean
	public EeApolloListener eeApolloListener() {
		return new EeApolloListener();
	}

	@Bean
	public EeConfigProperties eeDynamicFiledProperties() {
		return new EeConfigProperties();
	}
}
