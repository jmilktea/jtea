package com.jmilktea.sample.demo;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * @author huangyb1
 * @date 2020/7/20
 */
@Component
public class ApplicationUtil {

	private static ApplicationContext applicationContext = null;

	public ApplicationUtil(ApplicationContext applicationContext) {
		ApplicationUtil.applicationContext = applicationContext;
	}

	public static ApplicationContext getContext() {
		return applicationContext;
	}
}
