package com.jmilktea.sample.demo.mybean.mapper;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author huangyb1
 * @date 2022/5/25
 */
public class JdkProxy implements InvocationHandler {

	public <T> T createProxy(Class<T> mapperClass) {
		return (T) Proxy.newProxyInstance(mapperClass.getClassLoader(), new Class[]{mapperClass}, this);
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		System.out.println(method.getName() + " method executing...");
		return null;
	}
}
