package com.jmilktea.sample.demo.service.circular;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * @author huangyb1
 * @date 2022/4/2
 */
@Service
public class BSevice {

	//@Lazy 不会报错了
	@Autowired
	private ASevice aSevice;

	//@Async 不会出现循环依赖
	public void async(){
		System.out.println(aSevice.toString());
	}
}
