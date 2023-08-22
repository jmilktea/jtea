## 背景        
对于一些批任务处理或者耗时的操作，通常会采用多线程的思路去做，并行处理提升速度，为了避免多线程频繁的创建和销毁线程带来的开销，我们通常使用线程池去管理线程，jdk提供了ThreadPoolExecutor线程池。     

有时候我们可能会需要等线程池的所有任务都提交完，然后拿到执行的结果，例如我提交了10w的任务给线程池，想知道全部任务执行后，成功了多少个，失败了多少个，平均执行时间大概是多少。另外线程池本身是个黑盒子，外部不知道里面的运行情况，例如当前有多少个线程正在运行，是否达到最大线程数，队列是否有任务积压，平时可能开发可能拍脑袋一样随意定线程数和队列数，缺少监控。    

本次我们就提供一个加强版的线程池，我们取名为EnhanceExecutor，采用的是包装器的设计思路，在ThreadPoolExecutor上做增强，提供一下两个功能：   
1.可以指定要执行的任务数量，并且拿到这批任务的执行结果，如成功数、失败数、平均执行时间      
2.基于springboot actuctor，提供线程池指标收集，有了这些指标，就可以使用promethus进行收集，在grafana可视化监控         

## 实现     
我们先看怎么做监控，在springboot中actuctor可以用于收集各种指标，默认情况下，已经帮我们统计了进程jvm信息、接口访问时间、qps、日志等指标信息，actuactor是基于[micrometer](https://micrometer.io/)的封装，micrometer提供了一系列的Meter，例如Counter、Timer、Gague，用于收集不同类型的指标。 
Gague是度量的意思，用于收集一些变化的数据，例如线程池的队列任务积压数，可能会变大变小，那么就可以使用Gague来收集。    
要使用micrometer，需要创建一个MeterRegistry，然后把相关要监控的Meter注册到它上面去，在收集时就会遍历所有的指标，拿到对应的数据。    

为了可以使用spring @Async 注解，来使用线程池，EnhanceExecutor实现了Executor接口，我们看下它的构造函数。   
```
public class EnhanceExecutor implements Executor {

	private MeterRegistry mr;
	private String name;
	private ThreadPoolExecutor poolExecutor;

	private EnhanceExecutor() {
	}

	public EnhanceExecutor(String name,
			       MeterRegistry mr,
			       int corePoolSize,
			       int maximumPoolSize,
			       long keepAliveTime,
			       TimeUnit unit,
			       BlockingQueue<Runnable> workQueue,
			       ThreadFactory threadFactory,
			       RejectedExecutionHandler handler) {
		Assert.notNull(name, "name must not null");
		this.name = name;
		this.mr = mr;
		this.poolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);

		//metrics
		String tagName = "enhance.pool.name";
		Gauge.builder("enhance.pool.core.size", this, s -> s.poolExecutor.getCorePoolSize()).tags(tagName, name).register(mr);
		Gauge.builder("enhance.pool.max.size", this, s -> s.poolExecutor.getMaximumPoolSize()).tags(tagName, name).register(mr);
		Gauge.builder("enhance.pool.active.count", this, s -> s.poolExecutor.getActiveCount()).tags(tagName, name).register(mr);
		Gauge.builder("enhance.pool.pool.size", this, s -> s.poolExecutor.getPoolSize()).tags(tagName, name).register(mr);
		Gauge.builder("enhance.pool.largest.size", this, s -> s.poolExecutor.getLargestPoolSize()).tags(tagName, name).register(mr);
		Gauge.builder("enhance.pool.queue.size", this, s -> s.poolExecutor.getQueue().size()).tags(tagName, name).register(mr);
	}
}
```    
name表示线程池的名称，多个线程收集时需要通过名称区分。MeterRegistry由spring注入，这样我们才可以将Gauge注册进去。几个Gauge我们非常熟悉，都是线程池的数据，也是本次我们关注的指标。构造函数的参数覆盖了jdk ThreadPoolExecutor的参数，内部也是构造了一个线程对象。    

实现了Executor接口是为了可以使用spring @Async注解注入，其它的几个submit参数都是间接调用了ThreadPoolExecutor方法，这意味着我们可以想使用ThreadPoolExecutor一样来使用EnhanceExecutor。   
```
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
```

EnhanceExecutor的start方法用于提供一次批任务的执行，代码如下：   
```
	public Instance start(int countDownSize) {
		if (countDownSize < 0) {
			throw new IllegalArgumentException("countDownSize must great than equal zero");
		}
		return new Instance(poolExecutor, countDownSize);
	}
```
start的结果就是返回一个内部类Instance对象，它表示一次一批任务的执行，就像我们前面说到的，例如我要执行10w个任务，并且想知道它的执行结果，就需要使用Instance对象，它里面使用了LongAdder类来进行原子计数，LongAdder是jdk8提供的，与AtomicLong不同的是，AtomicLong内部只有一个value，多线程操作出现并发时就会进行cas自旋，在并发较大的时候会消耗较多的资源，LongAdder与ConcurrentHashMap的思想类似，采用分段的设计来较低热点资源的竞争，它内部维护了一个base和cells数组，当出现并发竞争的时候，多个线程可能是分布到cells数组的各个项去竞争，进而降低了资源的竞争，减少了cas的时间。     
同时还有一个CountDownLatch用于等待所有任务执行完成，这样我们才可以在await后拿到本次执行的结果。代码如下：   
```
public static class Instance {

		/**
		 * pool
		 */
		private String name;
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

		private Instance(String name, ThreadPoolExecutor poolExecutor, int countDownSize) {
			this.poolExecutor = poolExecutor;
			this.name = name;
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
					log.error("EnhanceExecutor {} execute error", this.name, e);
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
```

ExecuteResult也是一个内部类，表示一次批任务执行的结果，如下：   
```
public static class ExecuteResult {
		private Long successCount = 0L;
		private Long failCount = 0L;
		private Long expCount = 0L;
		private Exception firstExp;
		private Long avgExecuteTime;
}
```

## 使用    
使用非常简单，我们只需要注入一个bean，
```
	@Bean("myPool")
	public EnhanceExecutor enhanceExecutor(MeterRegistry mr) {
		return new EnhanceExecutor(
				"myPool",
				mr,
				10,
				100,
				60L,
				TimeUnit.SECONDS,
				new ArrayBlockingQueue<>(1000),
				new ThreadFactoryBuilder().setNameFormat("my-pool-%d").build(),
				new ThreadPoolExecutor.CallerRunsPolicy());
	}
```
接着就可以注入使用了
```
    @Autowired
    @Qualifier("myPool")
    private EnhanceExecutor myPool;

    int count = 100000;
    EnhanceExecutor.Instance instance = myPool.start(count);
	for (int i = 0; i < count; i++) {
		instance.execute(() -> {
			try {
				Thread.sleep(RandomUtils.nextInt(0, 200));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return true;
		});
	}
	EnhanceExecutor.ExecuteResult er = instance.await();
	log.info("execute result:{}", er);
```

同时我们指标会actuctor收集，我们可以返回 /actuator/prometheus 接口看到上面的指标，接着可以使用promethus对这些指标进行收集，用grafana展示    
![image](https://github.com/jmilktea/jmilktea/blob/master/%E8%AE%BE%E8%AE%A1/images/enhance-executor1.png)

