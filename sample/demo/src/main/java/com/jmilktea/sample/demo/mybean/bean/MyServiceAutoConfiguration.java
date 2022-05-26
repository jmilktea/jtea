package com.jmilktea.sample.demo.mybean.bean;


import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author huangyb1
 * @date 2022/5/24
 */
@Configuration
@Import(value = MyServiceRegistrar.class)
public class MyServiceAutoConfiguration {
}
