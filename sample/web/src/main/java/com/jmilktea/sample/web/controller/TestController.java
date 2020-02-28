package com.jmilktea.sample.web.controller;

import com.jmilktea.sample.web.core.ReactiveResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

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

    //master init
    //test_ff commit 1 test_ff commit 2
    //test_nf commit 1
}
