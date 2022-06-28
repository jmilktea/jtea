package com.jmilktea.sample.demo.enhance;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author huangyb1
 * @date 2022/6/20
 */
@Slf4j
public class ExecuteInstance {

	/**
	 * pool
	 */
	private ThreadPoolExecutor poolExecutor;

	/**
	 * execute
	 */
	private CountDownLatch countDownLatch;
	private LongAdder totalExecuteTime = new LongAdder();
	private LongAdder successCounter = new LongAdder();
	private LongAdder failCounter = new LongAdder();
	private LongAdder expCounter = new LongAdder();
	private Exception firstExp;

	private final static Object EXP_LOCK = new Object();

	public ExecuteInstance(ThreadPoolExecutor poolExecutor, int countDownSize) {
		this.poolExecutor = poolExecutor;
		this.countDownLatch = new CountDownLatch(countDownSize);
	}

	public void execute(Runnable task) {
		execute(() -> {
			task.run();
			return true;
		});
	}

	public void execute(Callable<Boolean> task) {
		poolExecutor.execute(() -> {
			boolean result = false;
			long startTime = System.currentTimeMillis();
			try {
				result = task.call();
			} catch (Exception e) {
				expCounter.increment();
				if (firstExp == null) {
					synchronized (EXP_LOCK) {
						if (firstExp == null) {
							firstExp = e;
						}
					}
				}
				log.error("Instance execute error", e);
			} finally {
				if (result) {
					successCounter.increment();
				} else {
					failCounter.increment();
				}
				countDownLatch.countDown();
				totalExecuteTime.add(System.currentTimeMillis() - startTime);
			}
		});
	}

	public ExecuteResult await() {
		try {
			countDownLatch.await();
		} catch (InterruptedException e) {
			log.error("countDown await error", e);
		}
		return getExecuteResult();
	}

	public ExecuteResult await(ExecuteResult lastResult) {
		ExecuteResult current = await();
		current.successCount += lastResult.successCount;
		current.failCount += lastResult.failCount;
		current.expCount += lastResult.expCount;
		current.totalExecuteTime += lastResult.totalExecuteTime;
		current.avgExecuteTime = calAvgExecuteTime(current.successCount + current.failCount, current.totalExecuteTime);
		return current;
	}

	private long calAvgExecuteTime(long totalCount, long totalExecuteTime) {
		if (totalCount == 0) {
			return 0L;
		}
		//总时间 / 总次数
		return totalExecuteTime / totalCount;
	}

	private ExecuteResult getExecuteResult() {
		ExecuteResult executeResult = new ExecuteResult();
		executeResult.setSuccessCount(successCounter.sum());
		executeResult.setFailCount(failCounter.sum());
		executeResult.setExpCount(expCounter.sum());
		executeResult.setTotalExecuteTime(totalExecuteTime.sum());
		executeResult.setAvgExecuteTime(calAvgExecuteTime(successCounter.sum() + failCounter.sum(), totalExecuteTime.sum()));
		executeResult.setFirstExp(firstExp);
		return executeResult;
	}

	@Data
	public class ExecuteResult {
		private long successCount = 0L;
		private long failCount = 0L;
		private long expCount = 0L;
		private long totalExecuteTime = 0L;
		private long avgExecuteTime = 0L;
		private Exception firstExp;
	}
}
