package com.jmilktea.sample.mybatis.spring;

import org.mybatis.spring.annotation.MapperScannerRegistrar;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.stereotype.Component;

/**
 * MapperScannerRegistrar
 *
 * @author huangyb1
 * @date 2021/8/2
 */

public class MyMapperScannerRegistrar implements ImportBeanDefinitionRegistrar {

	/**
	 * org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration.AutoConfiguredMapperScannerRegistrar#registerBeanDefinitions(org.springframework.core.type.AnnotationMetadata, org.springframework.beans.factory.support.BeanDefinitionRegistry)
	 * @param importingClassMetadata
	 * @param registry
	 */
	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(MyMapperScannerConfigurer.class);
		registry.registerBeanDefinition(MyMapperScannerConfigurer.class.getName(), builder.getBeanDefinition());
	}
}
