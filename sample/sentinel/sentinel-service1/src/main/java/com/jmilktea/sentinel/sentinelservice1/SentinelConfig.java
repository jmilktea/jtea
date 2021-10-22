package com.jmilktea.sentinel.sentinelservice1;

import com.alibaba.cloud.sentinel.feign.SentinelFeign;
import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangyb1
 * @date 2021/10/21
 */
//@Configuration
public class SentinelConfig {

	@Bean
	public SentinelResourceAspect sentinelResourceAspect() {
		return new SentinelResourceAspect();
	}
}
