package com.jmilktea.sample.web.controller;

import com.jmilktea.sample.web.core.ReactiveResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("test")
public class TestController {

	@GetMapping("ping")
	public Mono<String> ping() {
		return Mono.just("ping success");
	}

	@GetMapping("webresult")
	public Mono<ReactiveResult<String>> webResult() {
		return ReactiveResult.success("success");
	}

	@GetMapping("exp")
	public Mono<String> exp() {
		throw new NullPointerException();
	}

	@GetMapping("monoexp")
	public Mono<String> monoExp() {
		return Mono.error(new RuntimeException());
	}

	@GetMapping("error")
	public Mono<ReactiveResult> error() {
		return ReactiveResult.fail("1001", "1001 error");
	}

	static List<ReactiveResult> list1 = new ArrayList<>();
	static List<ReactiveResult> list2 = new ArrayList<>();
	static List<ReactiveResult> list3 = new ArrayList<>();
	static List<ReactiveResult> list4 = new ArrayList<>();
	static List<ReactiveResult> list5 = new ArrayList<>();
	@GetMapping("oom")
	public Mono<ReactiveResult> oom() {
		for (long i = 0; i < Long.MAX_VALUE; i++) {
			System.out.println(Runtime.getRuntime().freeMemory());
			list1.add(new ReactiveResult(Long.valueOf(i)));
			list2.add(new ReactiveResult(Long.valueOf(i)));
			list3.add(new ReactiveResult(Long.valueOf(i)));
			list4.add(new ReactiveResult(Long.valueOf(i)));
			list5.add(new ReactiveResult(Long.valueOf(i)));
		}
		return ReactiveResult.fail("0", "oom");
	}

	@GetMapping("oomtest")
	public Mono<ReactiveResult> oomtest() {
		System.out.println(Runtime.getRuntime().freeMemory());
		return ReactiveResult.success();
	}


	//master init
	//test_ff commit 1 test_ff commit 2
	//test_nf commit 1 test_nf commit 2
	//test_squash commit 1 test_squash commit 2
}
