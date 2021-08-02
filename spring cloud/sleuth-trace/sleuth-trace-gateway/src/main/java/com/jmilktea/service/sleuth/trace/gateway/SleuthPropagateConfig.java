package com.jmilktea.service.sleuth.trace.gateway;

import org.springframework.cloud.sleuth.autoconfig.SleuthProperties;
import org.springframework.cloud.sleuth.instrument.web.TraceWebServletAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.List;

/**
 * @author huangyb
 * @date 2020/1/21
 */
@Configuration
@Order(TraceWebServletAutoConfiguration.TRACING_FILTER_ORDER - 2)
public class SleuthPropagateConfig {

    @Bean
    public SleuthProperties sleuthProperties() {
        SleuthProperties sleuthProperties = new SleuthProperties();
        List<String> propagationKeys = sleuthProperties.getPropagationKeys();
        if (propagationKeys == null) {
            propagationKeys = new ArrayList<>();
        }
        if (!propagationKeys.contains("uid")) {
            propagationKeys.add("uid");
        }
        return sleuthProperties;
    }
}