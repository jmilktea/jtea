package com.jmilktea.sample.redisstreamserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = RedisStreamServerApplication.class)
class RedisStreamServerApplicationTests {

	@Autowired
	private RedisService redisService;

	private static InheritableThreadLocal inheritableThreadLocal = new InheritableThreadLocal();

	@Test
	public void testSendMessageToStream() throws InterruptedException {
		for (int thread = 0; thread < 10; thread++) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					for (int i = 0; i < 10; i++) {
						redisService.sendMessageToStream("stream-key-" + i, String.valueOf(i));
					}
				}
			}).start();
		}
		Thread.sleep(3000);
	}
}
