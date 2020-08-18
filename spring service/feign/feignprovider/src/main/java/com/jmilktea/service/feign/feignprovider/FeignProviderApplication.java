package com.jmilktea.service.feign.feignprovider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@EnableEurekaClient
@RestController
@SpringBootApplication
public class FeignProviderApplication {

	public static void main(String[] args) {
		SpringApplication.run(FeignProviderApplication.class, args);
	}

	@RequestMapping(value = "/provide", method = RequestMethod.GET)
	public String provide(Long id) {
		return "provide:" + id;
	}

	@RequestMapping(value = "/testTimeOut", method = RequestMethod.GET)
	public String testTimeOut() throws InterruptedException {
		Thread.sleep(20000);
		return "success";
	}
}
