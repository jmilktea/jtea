package com.jmilktea.service.gatewayfilter.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

/**
 * @author huangyb
 * @date 2019/12/5
 */
@Component
public class UserLogGatewayFilterFactory extends AbstractGatewayFilterFactory {

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            System.out.println("user gate way log");
            return chain.filter(exchange);
        };
    }
}
