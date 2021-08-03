package com.jmilktea.sample.mybatis.spring;

import org.springframework.beans.factory.FactoryBean;

/**
 * @author huangyb1
 * @date 2021/8/2
 */
public class MyFactoryBean implements FactoryBean {

	@Override
	public boolean isSingleton() {
		return true;
	}

	@Override
	public Object getObject() throws Exception {
		return new MyMapper();
	}

	@Override
	public Class<?> getObjectType() {
		return MyMapper.class;
	}
}
