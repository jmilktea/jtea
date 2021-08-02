package com.jmilktea.sample.demo.context;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * @author huangyb1
 * @date 2021/8/2
 */
@Component
public class ContextBeanPostProcessor implements BeanPostProcessor {

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if(bean.getClass() == SimpleBean.class){
			System.out.println("====ContextBeanPostProcessor " + ((SimpleBean)bean).getName());
		}
		return bean;
	}
}
