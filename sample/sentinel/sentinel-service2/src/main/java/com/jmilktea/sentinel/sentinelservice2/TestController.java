package com.jmilktea.sentinel.sentinelservice2;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author huangyb1
 * @date 2021/10/20
 */
@RestController
public class TestController {

	@GetMapping(value = "/test")
	public String test(int p) {
		System.out.println(p);
		if (p % 2 == 0) {
			return "success";
		}
		throw new IllegalArgumentException("p");
	}
}
