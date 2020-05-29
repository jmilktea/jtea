package com.jmilktea.sample.mybatis.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * @author huangyb1
 * @date 2020/5/29
 */
//@Component
@Slf4j
@Aspect
public class MapperAop {

	@Around("execution(public * com.jmilktea.sample.mybatis.mapper..*.*(..))")
	public void aroundMapper(JoinPoint joinPoint) {
		System.out.println("在这里做切面，mybatis拦截器就不会生效");
	}
}
