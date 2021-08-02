package com.jmilktea.sample.demo.context;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * @author huangyb1
 * @date 2021/8/2
 */
@Data
@Component
public class SimpleBean implements InitializingBean {

	private String name;

	@Override
	public void afterPropertiesSet() throws Exception {
		this.name = "simple bean";
		System.out.println("====context simple bean afterPropertiesSet");
	}
}
