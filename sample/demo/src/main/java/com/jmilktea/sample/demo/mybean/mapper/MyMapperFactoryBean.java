package com.jmilktea.sample.demo.mybean.mapper;

import org.springframework.beans.factory.FactoryBean;

/**
 * @author huangyb1
 * @date 2022/5/25
 */
public class MyMapperFactoryBean<T> implements FactoryBean<T> {

	private Class<T> mapperClass;
	private JdkProxy jdkProxy = new JdkProxy();

	public void setMapperClass(Class<T> mapperClass) {
		this.mapperClass = mapperClass;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	@Override
	public T getObject() throws Exception {
		//使用jdk动态代理生成代理对象
		return jdkProxy.createProxy(mapperClass);
	}

	@Override
	public Class<?> getObjectType() {
		return mapperClass;
	}
}
