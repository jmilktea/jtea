package com.jmilktea.sample.demo.enhance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author huangyb1
 * @date 2022/6/20
 */
@Slf4j
public class ExecuteInstance {

	/**
	 * executor
	 */
	private Executor executor;

	/**
	 * execute
	 */
	private CountDownLatch countDownLatch;
	private LongAdder totalExecuteTime = new LongAdder();
	private LongAdder successCounter = new LongAdder();
	private LongAdder failCounter = new LongAdder();
	private LongAdder filterCounter = new LongAdder();
	private LongAdder expCounter = new LongAdder();
	private Exception firstExp;

	private final static Object EXP_LOCK = new Object();

	public ExecuteInstance() {
	}

	public ExecuteInstance(Executor executor) {
		Assert.notNull(executor, "executor must not null");
		this.executor = executor;
	}

	public void setCountDownSize(int countDownSize) {
		if (countDownSize < 0) {
			throw new IllegalArgumentException("countDownSize must great than equal zero");
		}
		this.countDownLatch = new CountDownLatch(countDownSize);
	}

	public void execute(Runnable task) {
		execute(task, 1);
	}

	public void execute(Runnable task, int batch) {
		execute(() -> {
			task.run();
			return ReturnResult.SUCCESS;
		}, batch);
	}

	public void execute(Callable<ReturnResult> task) {
		execute(task, 1);
	}

	public void execute(Callable<ReturnResult> task, int batch) {
		if (executor != null) {
			executor.execute(() -> executeStatistics(task, batch));
		} else {
			executeStatistics(task, batch);
		}
	}

	private void executeStatistics(Callable<ReturnResult> task, int batch) {
		ReturnResult result = ReturnResult.NONE;
		long startTime = System.currentTimeMillis();
		try {
			result = task.call();
		} catch (Exception e) {
			expCounter.add(batch);
			if (firstExp == null) {
				synchronized (EXP_LOCK) {
					if (firstExp == null) {
						firstExp = e;
					}
				}
			}
			log.error("ExecuteInstance execute error", e);
		} finally {
			if (countDownLatch != null) {
				countDownLatch.countDown();
			}
			if (ReturnResult.SUCCESS.equals(result)) {
				successCounter.add(batch);
			} else if (ReturnResult.FAIL.equals(result)) {
				failCounter.add(batch);
			} else if (ReturnResult.FILTER.equals(result)) {
				filterCounter.add(batch);
			}
			totalExecuteTime.add(System.currentTimeMillis() - startTime);
		}
	}

	public ExecuteResult await() {
		if (countDownLatch != null) {
			try {
				countDownLatch.await();
			} catch (InterruptedException e) {
				log.error("countDown await error", e);
			}
		}
		return getExecuteResult();
	}

	private ExecuteResult getExecuteResult() {
		ExecuteResult executeResult = new ExecuteResult();
		executeResult.setSuccessCount(successCounter.sum());
		executeResult.setFailCount(failCounter.sum());
		executeResult.setFilterCount(filterCounter.sum());
		executeResult.setExpCount(expCounter.sum());
		executeResult.setTotalExecuteTime(totalExecuteTime.sum());
		executeResult.setFirstExp(firstExp);
		return executeResult;
	}
}
