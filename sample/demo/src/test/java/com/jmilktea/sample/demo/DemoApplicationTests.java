package com.jmilktea.sample.demo;

import com.jmilktea.sample.demo.service.TransService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DemoApplicationTests {

	@Autowired
	private TransService transService;

	@Test
	public void testTransaction() {
		System.out.println(transService.getClass());
		System.out.println(transService.getClass().getSuperclass());

		transService.testTransaction();
	}

	@Test
	public void testTransactionCall() {
		transService.testTransactionCall();
	}

	@Test
	public void testTransactionFinal() {
		transService.testTransactionFinal();
	}

	@Test
	public void testTransactional2() throws InterruptedException {
		transService.testTransactional2();
	}
}
