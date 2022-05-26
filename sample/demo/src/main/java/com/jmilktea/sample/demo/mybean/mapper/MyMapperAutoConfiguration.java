package com.jmilktea.sample.demo.mybean.mapper;


import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author huangyb1
 * @date 2022/5/24
 */
@Configuration
@Import(value = MyMapperRegistrar.class)
public class MyMapperAutoConfiguration {
}
