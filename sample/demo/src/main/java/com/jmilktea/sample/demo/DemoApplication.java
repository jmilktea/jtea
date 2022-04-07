package com.jmilktea.sample.demo;

import com.jmilktea.sample.demo.simple.TestBean;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		//git tag v1.2
		ConfigurableApplicationContext context = SpringApplication.run(DemoApplication.class, args);
		//context.close();
	}

	@Bean(initMethod = "initMethod", destroyMethod = "destroyMethod")
	public TestBean testBean() {
		return new TestBean();
	}
}
