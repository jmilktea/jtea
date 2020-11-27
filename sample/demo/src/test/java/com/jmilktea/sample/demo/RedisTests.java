package com.jmilktea.sample.demo;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class RedisTests {

	@Autowired
	private RedissonClient redissonClient;

	private static int COUNT = 0;

	@Test
	public void testRLock() throws InterruptedException {
		//100个线程
		ExecutorService executorService = Executors.newFixedThreadPool(100);
		for (int i = 0; i < 100; i++) {
			executorService.submit(() -> {
				//加锁
				RLock rLock = redissonClient.getLock("r_lock");
				rLock.lock();
				try {
					//业务操作
					COUNT++;
					System.out.println(Thread.currentThread().getId() + ":" + COUNT);
					if (COUNT == 3) {
						Thread.sleep(5000);
					} else {
						Thread.sleep(1000);
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				} finally {
					rLock.unlock();
				}
			});
		}
		Thread.sleep(Integer.MAX_VALUE);
	}
}
