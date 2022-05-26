package com.jmilktea.sample.demo.service;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Service;

/**
 * @author huangyb1
 * @date 2022/4/2
 */
@Service
public class ServiceFactoryBean implements FactoryBean<ServiceBean> {

	@Override
	public boolean isSingleton() {
		return true;
	}

	@Override
	public ServiceBean getObject() throws Exception {
		return new ServiceBean();
	}

	@Override
	public Class<?> getObjectType() {
		return ServiceBean.class;
	}
}
