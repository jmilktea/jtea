package com.jmilktea.service.feign.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@EnableEurekaClient
@RestController
@EnableFeignClients
@SpringBootApplication
public class FeignClientApplication {

	@Autowired
	private FeignProvider feignProvider;

	@Autowired
	private FeignProvider2 feignProvider2;

	@Autowired
	private FeignProvider3 feignProvider3;

	public static void main(String[] args) {
		SpringApplication.run(FeignClientApplication.class, args);
	}

	@RequestMapping(value = "/provide1", method = RequestMethod.GET)
	public String provide1(Long id) {
		return feignProvider.provide1("1a");
	}

	@RequestMapping(value = "/provide2", method = RequestMethod.GET)
	public String provide2(Long id) {
		return feignProvider2.provide2("1a");
	}

	@RequestMapping(value = "/testTimeOut3", method = RequestMethod.GET)
	public String testTimeOut3() {
		return feignProvider3.testTimeOut();
	}

	@RequestMapping(value = "/testTimeOut2", method = RequestMethod.GET)
	public String testTimeOut2() {
		return feignProvider2.testTimeOut();
	}
}
