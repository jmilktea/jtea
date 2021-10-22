package com.jmilktea.sentinel.sentinelservice1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author huangyb1
 * @date 2021/10/20
 */
@RestController
public class TestController {

	@Autowired
	private Service2ClientService service2ClientService;

	@GetMapping(value = "/test")
	public String test(int p) {
		service2ClientService.test(p);
		return null;
	}
}
