package com.jmilktea.sample.demo.service.circular;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author huangyb1
 * @date 2022/4/2
 */
@Service
public class CSevice {

	@Autowired
	private DSevice dSevice;

	@Transactional //不会循环依赖
	public void transactional(){
		System.out.println(dSevice.toString());
	}
}
