package com.jmilktea.service.feign.client;

import org.springframework.stereotype.Component;

/**
 * @author huangyb1
 * @date 2020/8/21
 */
@Component
public class FeignProvider2Fallback implements FeignProvider2 {

	@Override
	public String provide2(String id) {
		return null;
	}

	@Override
	public String testTimeOut() {
		System.out.println("time out");
		return null;
	}

	@Override
	public String testPostTimeOut() {
		System.out.println("time out");
		return null;
	}
}
