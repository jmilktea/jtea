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
public class AuthGlobalFilter implements GlobalFilter, Ordered {

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
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (path.startsWith("/public")) {
            return chain.filter(exchange);
        }
        //假设参数需要token
        String token = request.getQueryParams().getFirst("token");
        if (token == null) {
            DataBuffer dataBuffer = null;
            ServerHttpResponse response = exchange.getResponse();
            Result result = new Result(false, "token为空，鉴权失败");
            try {
                dataBuffer = response.bufferFactory().wrap(objectMapper.writeValueAsBytes(result));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
            return response.writeWith(Mono.just(dataBuffer));
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
