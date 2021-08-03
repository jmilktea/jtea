package com.jmilktea.sample.mybatis.spring;

import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;

import java.util.Set;

/**
 * org.mybatis.spring.mapper.ClassPathMapperScanner
 * @author huangyb1
 * @date 2021/8/2
 */
public class MyMapperScanner extends ClassPathBeanDefinitionScanner {

	public MyMapperScanner(BeanDefinitionRegistry registry) {
		super(registry);
	}

	@Override
	protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
		Set<BeanDefinitionHolder> beanDefinitionHolders = super.doScan(basePackages);
		for (BeanDefinitionHolder beanDefinitionHolder : beanDefinitionHolders) {
			if (beanDefinitionHolder.getBeanName().equals("simpleBean")) {
				((GenericBeanDefinition) beanDefinitionHolder.getBeanDefinition()).setBeanClass(MyFactoryBean.class);
			}
		}
		return beanDefinitionHolders;
	}
}
