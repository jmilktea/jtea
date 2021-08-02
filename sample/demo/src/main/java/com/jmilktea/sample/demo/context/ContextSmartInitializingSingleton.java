package com.jmilktea.sample.demo.context;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author huangyb1
 * @date 2021/8/2
 */
@Component
public class ContextSmartInitializingSingleton implements SmartInitializingSingleton {

	@Autowired
	private SimpleBean simpleBean;

	@Override
	public void afterSingletonsInstantiated() {
		System.out.println("====smart singleton " + simpleBean.getName());
	}
}
