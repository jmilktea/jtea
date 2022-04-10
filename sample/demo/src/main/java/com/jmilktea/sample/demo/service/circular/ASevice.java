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
public class ASevice {

	@Autowired
	private BSevice bSevice;
	@Autowired
	private AsyncExecutor asyncExecutor;

	//@Async //出现循环依赖
	public void async() {
		System.out.println(bSevice.toString());
	}

	@Service
	class AsyncExecutor {
		@Async
		public void async() {
			System.out.println(bSevice.toString());
		}
	}
}
