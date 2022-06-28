package com.jmilktea.sample.demo.bytebuddy;

/**
 * @author huangyb1
 * @date 2022/6/27
 */
public class TimeClass {

	@TimeWatch
	public void test() throws InterruptedException {
		System.out.println("test");
		Thread.sleep(1000);
	}

	public void test2() {
		System.out.println("test2");
		test2Inner();
	}

	@TimeWatch
	private void test2Inner() {

	}
}
