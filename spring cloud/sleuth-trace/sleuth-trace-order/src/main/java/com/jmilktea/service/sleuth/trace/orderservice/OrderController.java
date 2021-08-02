package com.jmilktea.service.sleuth.trace.orderservice;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * @author huangyb
 * @date 2020/1/21
 */
@Slf4j
@RestController
@RequestMapping("order")
public class OrderController {

    private static Scheduler scheduler = Schedulers.newParallel("order-scheduler", 100);

    @RequestMapping("test")
    public Mono<String> test() {
        Mono.just(1).subscribeOn(scheduler).subscribe(s -> {
            testScheduler();
        });
        log.info("============test log=========");
        return Mono.just("success");
    }

    private void testScheduler() {
        log.info("============test scheduler=========");
    }
}
