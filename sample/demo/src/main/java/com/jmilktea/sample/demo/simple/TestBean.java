package com.jmilktea.sample.demo.simple;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * @author huangyb1
 * @date 2020/7/28
 */
public class TestBean implements InitializingBean, DisposableBean {

	@PostConstruct
	public void init() {
		System.out.println("PostConstruct");
	}

	@PreDestroy
	public void destory() {
		System.out.println("PreDestroy");
	}

	public void initMethod() {
		System.out.println("initMethod");
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		System.out.println("afterPropertiesSet");
	}

	@Override
	public void destroy() throws Exception {
		System.out.println("destroy");
	}

	public void destroyMethod() {
		System.out.println("destroyMethod");
	}
}
