package com.jmilktea.service.gatewayfilter.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * @author huangyb
 * @date 2019/12/5
 */
@Component
public class CustomGlobalFilter implements GlobalFilter, Ordered {

    private static ObjectMapper objectMapper = new ObjectMapper();

    /**
     * ServerWebExchange 包装了Http Request和Response
     *
     * @param exchange
     * @param chain
     * @return
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        System.out.println("custom filter order 1");
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        //order越高优先级越低
        return 1;
    }
}
