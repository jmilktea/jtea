package com.jmilktea.sample.demo;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.LockSupport;

/**
 * @author huangyb1
 * @date 2022/3/30
 */
@SpringBootTest
public class WaitNotifyTests {

	private final Object lock = new Object();
	private List<Integer> list = Lists.newArrayList();
	private Random random = new Random();

	@Test
	public void testWaitAndNotifyDemo() throws InterruptedException {
		//线程1不断从list取出元素，如果空了就等线程2生产
		new Thread(() -> {
			while (true) {
				synchronized (lock) {
					if (list.isEmpty()) {
						try {
							System.out.println("consume number:empty");
							lock.wait();
						} catch (InterruptedException e) {
						}
					}
					System.out.println("consume number:");
					list.forEach(s -> System.out.println(s));
					list.removeAll(list);
				}
			}
		}).start();

		Thread.sleep(1000);

		//线程2每秒放一个元素到list
		new Thread(() -> {
			while (true) {
				synchronized (lock) {
					list.add(random.nextInt(100));
					lock.notify();
				}
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			}
		}).start();

		Thread.sleep(10000);
	}

	@Test
	public void testWaitAndNotify() throws InterruptedException {
		new Thread(() -> {
			synchronized (lock) {
				System.out.println("123");
				try {
					lock.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.out.println("456");
			}
		}).start();

		Thread.sleep(5000);

		new Thread(() -> {
			synchronized (lock) {
				lock.notify();
			}
		}).start();

		Thread.sleep(5000);
	}

	@Test
	public void testParkAndUnPark() throws InterruptedException {
		Thread thread = new Thread(() -> {
			System.out.println("123");
			LockSupport.park();
			//执行unpark后，会继续后面代码的执行
			System.out.println("456");
		});
		thread.start();

		Thread.sleep(5000);
		LockSupport.unpark(thread);
	}

	@Test
	public void testRunThreadTwice() throws InterruptedException {
		lock.wait();
		Thread thread = new Thread(() -> {
			System.out.println("123");
		});
		thread.start();
		Thread.sleep(1000);
		System.out.println(thread.getState());
		thread.start();
	}

	@Test
	public void testRunUnparkFirst() {
		LockSupport.unpark(Thread.currentThread());
		LockSupport.park();
		System.out.println("不会阻塞...");
	}
}
