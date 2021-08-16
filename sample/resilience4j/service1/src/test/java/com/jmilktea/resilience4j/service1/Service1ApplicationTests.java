package com.jmilktea.resilience4j.service1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = Service1Application.class)
class Service1ApplicationTests {

	@Autowired
	private Service2Client service2Client;

	@Test
	public void test() {
		for (int i = 0; i < 100; i++) {
			int p = i;
			try {
				//0-0，会触发熔断
				//10-20,会直接熔断
				//21-26,会调用，然后又回到熔断状态
				//27-30，会直接熔断
				//>30，会调用，然后进入正常状态
				if (i == 21) {
					Thread.sleep(3000);
				}
				if (i == 31) {
					Thread.sleep(3000);
				}
				if (i >= 31) {
					p = 0;
				}
				service2Client.test(p);
				System.out.println("success:" + i);
			} catch (Exception e) {
				System.out.println("fail:" + i);
			}
		}
	}

}
