package com.jmilktea.sample.demo;

import com.jmilktea.sample.demo.service.TransService;
import com.jmilktea.sample.demo.service.circular.BSevice;
import com.jmilktea.sample.demo.service.circular.IAsync;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.Test;
//import org.springframework.aop.support.AopUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@SpringBootTest(classes = DemoApplication.class)
public class DemoApplicationTests {

	@Autowired
	private TransService transService;

//	@Autowired
//	private IAsync bSevice;
	@Autowired
	private BSevice bSevice;

	@Test
	public void testTransaction() throws InterruptedException {
		bSevice.test();
		//AbstractAutowireCapableBeanFactory
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

//	@Test
//	public void testAsync() throws InterruptedException {
//		bSevice.async();
//		System.out.println("===done===");
//		Thread.sleep(5000);
//	}
}
