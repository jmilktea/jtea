package com.jmilktea.sample.demo.enhance;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * EnhanceExecutor实现了Executor接口，可以使用spring @Async注解开启异步
 * EnhanceExecutor会收集线程池相关指标，可以上报监控平台
 * 对于需要拿到本批次执行结果的，例如本次执行成功数量，平均执行时间，使用start方法
 * EnhanceExecutor支持ThreadPoolExecutor相关execute,submit方法
 *
 * @author huangyb1
 * @date 2022/2/25
 */
@Slf4j
public class EnhanceExecutor implements Executor {

	private ThreadPoolExecutor poolExecutor;

	public EnhanceExecutor(String name,
						   MeterRegistry mr,
						   int corePoolSize,
						   int maximumPoolSize,
						   long keepAliveSecond,
						   ResizableCapacityLinkedBlockingQueue<Runnable> workQueue
	) {
		this(name, mr, corePoolSize, false, 0, maximumPoolSize, keepAliveSecond,
				workQueue, Executors.defaultThreadFactory(), new EERejectedExecutionHandlerHolder.EEAbortPolicy());
	}

	public EnhanceExecutor(String name,
						   MeterRegistry mr,
						   int corePoolSize,
						   int maximumPoolSize,
						   long keepAliveSecond,
						   ResizableCapacityLinkedBlockingQueue<Runnable> workQueue,
						   ThreadFactory threadFactory) {
		this(name, mr, corePoolSize, false, 0, maximumPoolSize, keepAliveSecond,
				workQueue, threadFactory, new EERejectedExecutionHandlerHolder.EEAbortPolicy());
	}

	public EnhanceExecutor(String name,
						   MeterRegistry mr,
						   int corePoolSize,
						   int maximumPoolSize,
						   long keepAliveSecond,
						   ResizableCapacityLinkedBlockingQueue<Runnable> workQueue,
						   EERejectedExecutionHandlerHolder.EERejectedExecutionHandlerCounter handler) {
		this(name, mr, corePoolSize, false, 0, maximumPoolSize, keepAliveSecond,
				workQueue, Executors.defaultThreadFactory(), handler);
	}

	public EnhanceExecutor(String name,
						   MeterRegistry mr,
						   int corePoolSize,
						   boolean allowCoreThreadTimeOut,
						   int maximumPoolSize,
						   long keepAliveSecond,
						   ResizableCapacityLinkedBlockingQueue<Runnable> workQueue,
						   ThreadFactory threadFactory,
						   EERejectedExecutionHandlerHolder.EERejectedExecutionHandlerCounter handler) {
		this(name, mr, corePoolSize, allowCoreThreadTimeOut, 0, maximumPoolSize, keepAliveSecond,
				workQueue, threadFactory, handler);
	}

	public EnhanceExecutor(String name,
						   MeterRegistry mr,
						   int corePoolSize,
						   boolean allowCoreThreadTimeOut,
						   int preStartCoreThread,
						   int maximumPoolSize,
						   long keepAliveSecond,
						   ResizableCapacityLinkedBlockingQueue<Runnable> workQueue,
						   ThreadFactory threadFactory,
						   EERejectedExecutionHandlerHolder.EERejectedExecutionHandlerCounter handler) {
		Assert.notNull(name, "name must not null");
		if (EnhanceExecutorContainer.MAP.containsKey(name)) {
			throw new IllegalArgumentException(name + " pool has register");
		}

		this.poolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveSecond, TimeUnit.SECONDS, workQueue, threadFactory, handler);
		this.poolExecutor.allowCoreThreadTimeOut(allowCoreThreadTimeOut);
		if (preStartCoreThread == 1) {
			this.poolExecutor.prestartCoreThread();
		} else if (preStartCoreThread > 1) {
			this.poolExecutor.prestartAllCoreThreads();
		}

		//register
		EnhanceExecutorContainer.MAP.put(name, this);

		//metrics
		String tagName = "enhance.pool.name";
		Gauge.builder("enhance.pool.coreSize", this, s -> s.poolExecutor.getCorePoolSize()).tags(tagName, name).register(mr);
		Gauge.builder("enhance.pool.maxSize", this, s -> s.poolExecutor.getMaximumPoolSize()).tags(tagName, name).register(mr);
		Gauge.builder("enhance.pool.activeCount", this, s -> s.poolExecutor.getActiveCount()).tags(tagName, name).register(mr);
		Gauge.builder("enhance.pool.poolSize", this, s -> s.poolExecutor.getPoolSize()).tags(tagName, name).register(mr);
		Gauge.builder("enhance.pool.largestSize", this, s -> s.poolExecutor.getLargestPoolSize()).tags(tagName, name).register(mr);
		Gauge.builder("enhance.pool.queueSize", this, s -> workQueue.size()).tags(tagName, name).register(mr);

		//完成任务数
		Gauge.builder("enhance.pool.completeCount", this, s -> s.poolExecutor.getCompletedTaskCount()).tags(tagName, name).register(mr);
		//队列初始容量
		Gauge.builder("enhance.pool.queueCapacity", this, s -> workQueue.getCapacity()).tags(tagName, name).register(mr);
		//队列剩余容量
		Gauge.builder("enhance.pool.queueRemainingCapacity", this, s -> workQueue.remainingCapacity()).tags(tagName, name).register(mr);
		//拒绝数量
		Gauge.builder("enhance.pool.rejectCount", this, s -> handler.get()).tags(tagName, name).register(mr);
	}

	public Instance start(int countDownSize) {
		if (countDownSize < 0) {
			throw new IllegalArgumentException("countDownSize must great than equal zero");
		}
		return new Instance(poolExecutor, countDownSize);
	}

	@Override
	public void execute(Runnable command) {
		poolExecutor.execute(command);
	}

	public Future<?> submit(Runnable task) {
		return poolExecutor.submit(task);
	}

	public <T> Future<T> submit(Callable<T> task) {
		return poolExecutor.submit(task);
	}

	public <T> Future<T> submit(Runnable task, T result) {
		return poolExecutor.submit(task, result);
	}

	public int getCorePoolSize() {
		return poolExecutor.getCorePoolSize();
	}

	public void setCorePoolSize(int corePoolSize) {
		poolExecutor.setCorePoolSize(corePoolSize);
	}

	public int getMaximumPoolSize() {
		return poolExecutor.getMaximumPoolSize();
	}

	public void setMaximumPoolSize(int maximumPoolSize) {
		poolExecutor.setMaximumPoolSize(maximumPoolSize);
	}

	public long getKeepAliveSecond() {
		return poolExecutor.getKeepAliveTime(TimeUnit.SECONDS);
	}

	public void setKeepAliveSecond(long keepAliveSecond) {
		poolExecutor.setKeepAliveTime(keepAliveSecond, TimeUnit.SECONDS);
	}

	public int getQueueCapacity() {
		return ((ResizableCapacityLinkedBlockingQueue) poolExecutor.getQueue()).getCapacity();
	}

	public void setQueueCapacity(int capacity) {
		((ResizableCapacityLinkedBlockingQueue) poolExecutor.getQueue()).setCapacity(capacity);
	}

	public static class Instance {

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

		private Instance() {
		}

		private Instance(ThreadPoolExecutor poolExecutor, int countDownSize) {
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

		private Long calAvgExecuteTime() {
			long totalCount = successCounter.sum() + failCounter.sum();
			if (totalCount == 0) {
				return 0L;
			}
			//总时间 / 总次数
			return totalExecuteTime.sum() / totalCount;
		}

		private ExecuteResult getExecuteResult() {
			ExecuteResult executeResult = new ExecuteResult();
			executeResult.setSuccessCount(successCounter.sum());
			executeResult.setFailCount(failCounter.sum());
			executeResult.setExpCount(expCounter.sum());
			executeResult.setAvgExecuteTime(calAvgExecuteTime());
			executeResult.setFirstExp(firstExp);
			return executeResult;
		}
	}

	@Data
	public static class ExecuteResult {
		private Long successCount = 0L;
		private Long failCount = 0L;
		private Long expCount = 0L;
		private Exception firstExp;
		private Long avgExecuteTime;
	}
}
