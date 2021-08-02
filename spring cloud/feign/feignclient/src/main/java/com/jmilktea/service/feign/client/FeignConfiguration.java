package com.jmilktea.service.feign.client;

import feign.Contract;
import feign.Feign;
import feign.Logger;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huangyb
 * @date 2019/12/12
 */
@Configuration
public class FeignConfiguration {

    @Bean
    public Logger.Level level() {
        return Logger.Level.FULL;
    }

    @Bean
    public FeignProvider3 feignProvider3() {
        return Feign.builder().contract(new SpringMvcContract()).requestInterceptor(new FeignInterceptor()).target(FeignProvider3.class, "localhost:8081");
    }
}
