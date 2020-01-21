package com.jmilktea.service.sleuth.trace.gateway;

import brave.Tracer;
import brave.propagation.ExtraFieldPropagation;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * @author huangyb
 * @date 2019/12/5
 */
@Component
@Slf4j
public class LogGlobalFilter implements GlobalFilter, Ordered {

    @Autowired
    private Tracer tracer;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        MDC.put("uid", "123");
        ExtraFieldPropagation.set("uid", "123");
        //log.info("============gate way log=========");
        return chain.filter(exchange).doFirst(() -> {
            log.info("============gate way log=========");
        }).doFinally(s -> {
            MDC.clear();
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
