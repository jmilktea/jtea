package com.jmilktea.sample.web.controller;

import com.jmilktea.sample.web.core.ReactiveResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("test")
public class TestController {

    @RequestMapping("ping")
    public Mono<String> ping() {
        return Mono.just("ping success");
    }

    @RequestMapping("webresult")
    public Mono<ReactiveResult> webResult() {
        return ReactiveResult.success("success");
    }

    @RequestMapping("exp")
    public Mono<String> exp() {
        throw new NullPointerException();
    }

    @RequestMapping("monoexp")
    public Mono<String> monoExp() {
        return Mono.error(new RuntimeException());
    }

    @RequestMapping("error")
    public Mono<ReactiveResult> error() {
        return ReactiveResult.fail("1001", "1001 error");
    }
}
