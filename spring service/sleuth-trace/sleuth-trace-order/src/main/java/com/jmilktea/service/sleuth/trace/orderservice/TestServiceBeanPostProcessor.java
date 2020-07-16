package com.jmilktea.service.sleuth.trace.orderservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Service;

/**
 * @author huangyb1
 * @date 2020/6/22
 */
//@Service
@Slf4j
public class TestServiceBeanPostProcessor implements BeanPostProcessor {

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof TestService) {
			return new TestServiceProxy();
		}
		return bean;
	}
}
