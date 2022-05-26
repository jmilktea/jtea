package com.jmilktea.sample.demo.mybean.mapper;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;

/**
 * @author huangyb1
 * @date 2022/5/26
 */
public class MyMapperScanner extends ClassPathBeanDefinitionScanner {

	public MyMapperScanner(BeanDefinitionRegistry registry) {
		super(registry);
	}

	@Override
	public int scan(String... basePackages) {
		return super.scan(basePackages);
	}

	@Override
	protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
		boolean b = beanDefinition.getMetadata().hasAnnotation(MyMapper.class.getName());
		if (b) {
			beanDefinition.setAttribute("mapperClass", beanDefinition.getMetadata().getClassName());
		}
		return b;
	}

	@Override
	protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry) {
		GenericBeanDefinition beanDefinition = ((GenericBeanDefinition) definitionHolder.getBeanDefinition());
		beanDefinition.setBeanClass(MyMapperFactoryBean.class);
		beanDefinition.getPropertyValues().add("mapperClass", beanDefinition.getAttribute("mapperClass"));
		super.registerBeanDefinition(definitionHolder, registry);
	}
}
