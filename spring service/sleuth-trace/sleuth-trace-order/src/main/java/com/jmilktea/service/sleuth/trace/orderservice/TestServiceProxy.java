package com.jmilktea.service.sleuth.trace.orderservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * @author huangyb1
 * @date 2020/6/22
 */
@Service
@Slf4j
public class TestServiceProxy extends TestService{

	@Async
	@Override
	public void async() {
		log.info("proxy async");
	}
}
