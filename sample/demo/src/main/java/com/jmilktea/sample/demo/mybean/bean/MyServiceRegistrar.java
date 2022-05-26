package com.jmilktea.sample.demo.mybean.bean;


import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * @author huangyb1
 * @date 2022/5/24
 */
public class MyServiceRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

	private ResourceLoader resourceLoader;

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		ClassPathBeanDefinitionScanner classPathBeanDefinitionScanner = new ClassPathBeanDefinitionScanner(registry);
		classPathBeanDefinitionScanner.setResourceLoader(resourceLoader);
		classPathBeanDefinitionScanner.addIncludeFilter(new AnnotationTypeFilter(MyService.class));
		classPathBeanDefinitionScanner.scan("com.jmilktea.sample.demo.mybean");
	}
}
