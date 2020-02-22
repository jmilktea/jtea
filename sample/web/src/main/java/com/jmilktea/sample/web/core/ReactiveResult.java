package com.jmilktea.sample.web.core;

import lombok.Data;
import reactor.core.publisher.Mono;

@Data
public class ReactiveResult<T> {
    private Boolean success;
    private T data;
    private String code;
    private String message;

    public final static ReactiveResult SUCCESS = new ReactiveResult(true);

    public ReactiveResult(Boolean success) {
        this.success = success;
    }

    public ReactiveResult(T data) {
        this.success = true;
        this.data = data;
    }

    public ReactiveResult(String message) {
        this(null, message);
    }

    public ReactiveResult(String code, String message) {
        this.success = false;
        this.code = code;
        this.message = message;
    }

    public static Mono<ReactiveResult> success() {
        return Mono.just(ReactiveResult.SUCCESS);
    }

    public static <T> Mono<ReactiveResult<T>> success(T data) {
        return Mono.just(new ReactiveResult(data));
    }

    public static Mono<ReactiveResult> fail(String message) {
        return ReactiveResult.fail(null, message);
    }

    public static Mono<ReactiveResult> fail(String code, String message) {
        return Mono.just(new ReactiveResult(code, message));
    }
}

