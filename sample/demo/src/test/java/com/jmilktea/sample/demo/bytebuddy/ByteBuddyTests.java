package com.jmilktea.sample.demo.bytebuddy;

import com.jmilktea.sample.demo.DemoApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest(classes = DemoApplication.class)
public class ByteBuddyTests {

	class TestClass {

		@TimeWatch
		public void test() throws InterruptedException {
			System.out.println("test");
			Thread.sleep(1000);
		}

		public void test2() {
			System.out.println("test2");
		}

	}

	@Test
	public void test() throws InterruptedException {
		TestClass testClass = new TestClass();
		testClass.test();
		testClass.test2();
	}
}
