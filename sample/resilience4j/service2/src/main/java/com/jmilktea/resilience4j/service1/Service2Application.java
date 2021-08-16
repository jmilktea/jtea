package com.jmilktea.resilience4j.service1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class Service2Application {

	public static void main(String[] args) {
		SpringApplication.run(Service2Application.class, args);
	}

	@GetMapping(value = "/test")
	public String test(int p) {
		System.out.println(p);
		if (p % 2 == 0) {
			return "success";
		}
		throw new IllegalArgumentException("p");
	}
}
