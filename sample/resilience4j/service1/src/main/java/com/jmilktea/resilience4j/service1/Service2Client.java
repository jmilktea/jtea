package com.jmilktea.resilience4j.service1;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author lushuaifei
 * @date 2021/3/16 17:58
 */
@FeignClient(name = "service2", url = "localhost:8082")
public interface Service2Client {

	@GetMapping("/test")
	String test(@RequestParam("p") int p);
}
