package com.jmilktea.sample.demo.enhance;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author huangyb1
 * @date 2022/6/21
 */
@Data
@Component
@ConfigurationProperties
public class EeConfigProperties {

	private Map<String, EeConfig> ee = new HashMap<>();

	public EeConfig getConfig(String key) {
		return ee.get(key);
	}

	@Data
	public static class EeConfig {

		private Integer corePoolSize;

		private Integer maximumPoolSize;

		private Long keepAliveSecond;

		private Integer queueCapacity;
	}
}
