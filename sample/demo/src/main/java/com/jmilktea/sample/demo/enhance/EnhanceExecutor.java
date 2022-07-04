package com.jmilktea.sample.demo.enhance;

import com.al.risk.collection.shutdown.Shutdown;
import com.al.risk.collection.shutdown.ShutdownRegistry;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Ee实现了Executor接口，可以使用spring @Async注解开启异步
 * <p>Ee会收集线程池相关指标，可以上报监控平台
 * <p>对于需要拿到本批次执行结果的，例如本次执行成功数量，平均执行时间，使用start方法
 * <p>Ee支持ThreadPoolExecutor相关execute,submit方法
 * <p>Ee支持核心线程过期，线程池预热
 * <p>Ee支持apollo动态修改线程池核心参数
 * <p>Ee支持打印日志trace id 和 span id
 *
 * @author huangyb1
 * @date 2022/2/25
 */
@Slf4j
public class EnhanceExecutor implements ExecutorService, InitializingBean {

	private String poolName;
	private ThreadPoolExecutor poolExecutor;

	@Autowired
	private EeConfigProperties eeConfigProperties;
	@Autowired
	private MeterRegistry mr;

	private EnhanceExecutor() {
	}

	public EnhanceExecutor(String poolName, int corePoolSize, int maximumPoolSize, long keepAliveSecond,
						   BlockingQueue<Runnable> workQueue) {
		this(poolName, corePoolSize, maximumPoolSize, keepAliveSecond, true, 0,
				workQueue, null, null);
	}

	public EnhanceExecutor(String poolName, int corePoolSize, int maximumPoolSize, long keepAliveSecond,
						   BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
		this(poolName, corePoolSize, maximumPoolSize, keepAliveSecond, true, 0,
				workQueue, threadFactory, null);
	}

	public EnhanceExecutor(String poolName, int corePoolSize, int maximumPoolSize, long keepAliveSecond,
						   BlockingQueue<Runnable> workQueue, EeRejectedExecutionHandler handler) {
		this(poolName, corePoolSize, maximumPoolSize, keepAliveSecond, true, 0,
				workQueue, null, handler);
	}

	public EnhanceExecutor(String poolName, int corePoolSize, int maximumPoolSize, long keepAliveSecond,
						   boolean allowCoreThreadTimeOut, BlockingQueue<Runnable> workQueue) {
		this(poolName, corePoolSize, maximumPoolSize, keepAliveSecond, allowCoreThreadTimeOut, 0,
				workQueue, null, null);
	}

	public EnhanceExecutor(String poolName, int corePoolSize, int maximumPoolSize, long keepAliveSecond,
						   boolean allowCoreThreadTimeOut, BlockingQueue<Runnable> workQueue, EeRejectedExecutionHandler handler) {
		this(poolName, corePoolSize, maximumPoolSize, keepAliveSecond, allowCoreThreadTimeOut, 0,
				workQueue, null, handler);
	}

	public EnhanceExecutor(String poolName, int corePoolSize, int maximumPoolSize, long keepAliveSecond,
						   boolean allowCoreThreadTimeOut, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, EeRejectedExecutionHandler handler) {
		this(poolName, corePoolSize, maximumPoolSize, keepAliveSecond, allowCoreThreadTimeOut, 0,
				workQueue, threadFactory, handler);
	}

	public EnhanceExecutor(String poolName, int corePoolSize, int maximumPoolSize, long keepAliveSecond,
						   boolean allowCoreThreadTimeOut, int preStartCoreThread,
						   BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, EeRejectedExecutionHandler handler) {
		Assert.notNull(poolName, "name must not null");

		this.poolName = poolName;
		if (threadFactory == null) {
			threadFactory = new ThreadFactoryBuilder().setNameFormat(poolName + "-%d").build();
		}
		if (handler == null) {
			handler = new EeRejectedExecutionHandler.EeCallerRunsPolicy();
		}
		this.poolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveSecond, TimeUnit.SECONDS, workQueue, threadFactory, handler);
		this.poolExecutor.allowCoreThreadTimeOut(allowCoreThreadTimeOut);
		if (preStartCoreThread >= corePoolSize) {
			this.poolExecutor.prestartAllCoreThreads();
		} else if (preStartCoreThread >= 1) {
			this.poolExecutor.prestartCoreThread();
		}
	}

	public ExecuteInstance getInstance() {
		return new ExecuteInstance(this);
	}

	@Override
	public void execute(Runnable command) {
		poolExecutor.execute(command);
	}

	@Override
	public Future<?> submit(Runnable task) {
		return poolExecutor.submit(task);
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		return poolExecutor.submit(task);
	}

	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		return poolExecutor.submit(task, result);
	}

	@Override
	public void shutdown() {
		poolExecutor.shutdown();
	}

	@Override
	public List<Runnable> shutdownNow() {
		return poolExecutor.shutdownNow();
	}

	@Override
	public boolean isShutdown() {
		return poolExecutor.isShutdown();
	}

	@Override
	public boolean isTerminated() {
		return poolExecutor.isTerminated();
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return poolExecutor.awaitTermination(timeout, unit);
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
		return poolExecutor.invokeAll(tasks);
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
		return poolExecutor.invokeAll(tasks, timeout, unit);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
		return poolExecutor.invokeAny(tasks);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		return poolExecutor.invokeAny(tasks, timeout, unit);
	}

	@Override
	public void afterPropertiesSet() {
		refreshPool();
		metrics();
		registerShutdown();
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
		if (keepAliveSecond > 0) {
			poolExecutor.setKeepAliveTime(keepAliveSecond, TimeUnit.SECONDS);
		}
	}

	public int getQueueCapacity() {
		if (poolExecutor.getQueue() instanceof ResizableCapacityLinkedBlockingQueue) {
			return ((ResizableCapacityLinkedBlockingQueue) poolExecutor.getQueue()).getCapacity();
		}
		return 0;
	}

	public void setQueueCapacity(int capacity) {
		if (capacity > 0 && poolExecutor.getQueue() instanceof ResizableCapacityLinkedBlockingQueue) {
			((ResizableCapacityLinkedBlockingQueue) poolExecutor.getQueue()).setCapacity(capacity);
		}
	}

	private void refreshPool() {
		//config value
		if (eeConfigProperties != null) {
			EeConfigProperties.EeConfig eeConfig = eeConfigProperties.getConfig(poolName);
			if (eeConfig != null) {
				int corePoolSize = eeConfig.getCorePoolSize() != null ? eeConfig.getCorePoolSize() : poolExecutor.getCorePoolSize();
				int maximumPoolSize = eeConfig.getMaximumPoolSize() != null ? eeConfig.getMaximumPoolSize() : poolExecutor.getMaximumPoolSize();
				if (corePoolSize > maximumPoolSize) {
					throw new BeanCreationException("create " + poolName + " error," +
							"corePoolSize:" + corePoolSize + " great than maximumPoolSize:" + maximumPoolSize);
				}
				if (eeConfig.getCorePoolSize() != null) {
					setCorePoolSize(eeConfig.getCorePoolSize());
				}
				if (eeConfig.getMaximumPoolSize() != null) {
					setMaximumPoolSize(eeConfig.getMaximumPoolSize());
				}
				if (eeConfig.getKeepAliveSecond() != null) {
					setKeepAliveSecond(eeConfig.getKeepAliveSecond());
				}
				if (eeConfig.getQueueCapacity() != null) {
					setQueueCapacity(eeConfig.getQueueCapacity());
				}
			}
		}
	}

	private void metrics() {
		if (mr != null) {
			String tagName = "enhance.pool.name";
			Gauge.builder("enhance.pool.corePoolSize", this, s -> poolExecutor.getCorePoolSize()).tags(tagName, poolName).register(mr);
			Gauge.builder("enhance.pool.maximumPoolSize", this, s -> poolExecutor.getMaximumPoolSize()).tags(tagName, poolName).register(mr);
			Gauge.builder("enhance.pool.activeCount", this, s -> poolExecutor.getActiveCount()).tags(tagName, poolName).register(mr);
			Gauge.builder("enhance.pool.poolSize", this, s -> poolExecutor.getPoolSize()).tags(tagName, poolName).register(mr);
			Gauge.builder("enhance.pool.largestPoolSize", this, s -> poolExecutor.getLargestPoolSize()).tags(tagName, poolName).register(mr);
			Gauge.builder("enhance.pool.completeTaskCount", this, s -> poolExecutor.getCompletedTaskCount()).tags(tagName, poolName).register(mr);
			Gauge.builder("enhance.pool.queueSize", this, s -> poolExecutor.getQueue().size()).tags(tagName, poolName).register(mr);
			Gauge.builder("enhance.pool.queueRemainingCapacity", this, s -> poolExecutor.getQueue().remainingCapacity()).tags(tagName, poolName).register(mr);
			Gauge.builder("enhance.pool.rejectCount", this, s -> ((EeRejectedExecutionHandler) poolExecutor.getRejectedExecutionHandler()).getRejectCount()).tags(tagName, poolName).register(mr);
			if (poolExecutor.getQueue() instanceof ResizableCapacityLinkedBlockingQueue) {
				Gauge.builder("enhance.pool.queueCapacity", this, s -> ((ResizableCapacityLinkedBlockingQueue) poolExecutor.getQueue()).getCapacity()).tags(tagName, poolName).register(mr);
			}
		}
	}

	private void registerShutdown() {
		ShutdownRegistry.register(new Shutdown(s -> this.poolExecutor.shutdown()));
	}
}
