package com.jmilktea.sample.demo.service;

import com.jmilktea.sample.demo.mapper.AccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * @author huangyb1
 * @date 2020/9/2
 */
@Service
//@Transactional(rollbackFor = Exception.class)
public class TransService {

	@Autowired
	private AccountMapper accountMapper;

	@Transactional(rollbackFor = Exception.class)
	public void testTransaction() {
		accountMapper.insert(14);
		String s = null;
		int l = s.length();
		accountMapper.insert(15);
	}

	public void testTransactionCall() {
		testTransaction();
	}

	//@Transactional(rollbackFor = Exception.class)
	final public void testTransactionFinal() {
		accountMapper.insert(14);
		String s = null;
		int l = s.length();
		accountMapper.insert(15);
	}

	public static volatile boolean IS_OK = true;

	@Transactional(rollbackFor = Exception.class)
	public void testTransactional2() throws InterruptedException {
		accountMapper.insert(99);
		accountMapper.insert(100);

		for (int i = 0; i < 10; i++) {
			final int uid = i;
			Executors.newFixedThreadPool(10).submit(() -> {
				System.out.println(MessageFormat.format("thread:{0} run", Thread.currentThread().getId()));
				accountMapper.insert(uid);
				if (uid == 5) {
					throw new RuntimeException("throw exception");
				}
			});
		}
		Thread.sleep(10000);
	}

	public void transWithCountDownLatch(){
		//子线程等待主线程通知
		CountDownLatch mainMonitor = new CountDownLatch(1);
		int threadCount = 5;
		CountDownLatch childMonitor = new CountDownLatch(threadCount);
		//子线程运行结果
		List<Boolean> childResponse = new ArrayList<Boolean>();
		ExecutorService executor = Executors.newCachedThreadPool();
		for (int i = 0; i < threadCount; i++) {
			int finalI = i;
			executor.execute(() -> {
				try {
					System.out.println(Thread.currentThread().getName() + "：开始执行");
					TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(1000));
					childResponse.add(Boolean.TRUE);
					childMonitor.countDown();
					System.out.println(Thread.currentThread().getName() + "：准备就绪,等待其他线程结果,判断是否事务提交");
					mainMonitor.await();
					if (IS_OK) {
						System.out.println(Thread.currentThread().getName() + "：事务提交");
					} else {
						System.out.println(Thread.currentThread().getName() + "：事务回滚");
					}
				} catch (Exception e) {
					childResponse.add(Boolean.FALSE);
					childMonitor.countDown();
					System.out.println(Thread.currentThread().getName() + "：出现异常,开始事务回滚");
				}
			});
		}
		//主线程等待所有子线程执行response
		try {
			childMonitor.await();
			for (Boolean resp : childResponse) {
				if (!resp) {
					//如果有一个子线程执行失败了，则改变mainResult，让所有子线程回滚
					System.out.println(Thread.currentThread().getName()+":有线程执行失败，标志位设置为false");
					IS_OK = false;
					break;
				}
			}
			//主线程获取结果成功，让子线程开始根据主线程的结果执行（提交或回滚）
			mainMonitor.countDown();
			//为了让主线程阻塞，让子线程执行。
			Thread.currentThread().join();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
