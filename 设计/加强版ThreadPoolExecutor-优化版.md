[上一篇](https://github.com/jmilktea/jtea/blob/master/%E8%AE%BE%E8%AE%A1/%E5%8A%A0%E5%BC%BA%E7%89%88ThreadPoolExecutor.md)我们介绍了加强版的ThreadPoolExecutor，主要是在jdk线程池的基础上增加了监控的功能，把监控指标暴露给spring boot actuator，可以收集到prometheus和grafana做看板监控和告警。     
美团有一篇技术博客:[Java线程池实现原理及其在美团业务中的实践](https://tech.meituan.com/2020/04/02/java-pooling-pratice-in-meituan.html)详细介绍了线程池的实现原理和动态化配置核心参数、监控告警的实现思路，该文章介绍得非常全面，值得反复阅读，遗憾的是美团并没有开源相关代码，不过github上有人基于这个思路做了实现：[hippo4j](https://github.com/mabaiwan/hippo4j)，可以看到监控的内容和我们上一篇介绍的基本上是类似的，这个框架的功能比较多，有点重，而我想要的是结合自己的需要、轻量、容易使用，例如后面加入了优雅下线的功能，可以参考[这篇文章](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/%E6%9C%8D%E5%8A%A1%E4%BC%98%E9%9B%85%E4%B8%8B%E7%BA%BF.md)。使用只是一个方面，关键在于在其中能学到知识，运用到实际的工作中。        

![image](https://github.com/jmilktea/jtea/blob/master/%E8%AE%BE%E8%AE%A1/images/enhance-executor-2-1.png)     

这篇也是对上面文章读后的一个思考，例如对比下图美团的监控参数，发现有些参数我们没有，那他是怎么做到的呢？    
- 有些参数可以直接从线程池获得，如线程池完成任务数completeTaskCount，队列剩余容量remainingCapacity。         
- 有些参数无法直接从线程池获得，如队列初始容量，例如我们使用LinkedBlockingQueue，capacity是一个私有变量，无法直接获得，同样也无法直接修改它。还有被拒绝策略拒绝的数量。        
- 如何与配置中心集成，做到参数动态修改，核心参数的动态修改还是很有必要的，从美团那篇文章可以看到，现在没有一个万能的公式可以计算出线程池最优的线程数，还是需要根据实际业务场景判断，而这很可能会出错，例如配置的参数过小，可能会导致任务积压，响应不及时，配置参数过大，可能会占用过多机器资源，或者造成下游服务的压力过大。我们之前就试过一些跑任务的线程池，设置参数较大，由于部署了多个实例，并发调用次数就成倍增加了，导致下游的服务扛不住，所以动态配置线程池参数还是有必要的，这样可以根据实际情况在不重启服务的情况下动态调整。  
 
![image](https://github.com/jmilktea/jtea/blob/master/%E8%AE%BE%E8%AE%A1/images/enhance-executor-2-2.png)   

## 实现    
对于可以直接从线程池获得的参数，不用多说，直接上报一下就行了，同样可以通过/actuator/prometheus看到相关指标         
```
//完成任务数
Gauge.builder("enhance.pool.completeCount", this, s -> s.poolExecutor.getCompletedTaskCount()).tags(tagName, name).register(mr);
//队列剩余容量
Gauge.builder("enhance.pool.queueRemainingCapacity", this, s -> workQueue.remainingCapacity()).tags(tagName, name).register(mr);
```

**线程池预热**    
这是一个高频面试题，ThreadPoolTaskExecutor的corePoolSize并不会一开始就创建，创建好默认不会销毁，除非配置allowCoreThreadTimeout参数。所以一旦任务进来，需要逐渐的创建核心线程，创建线程是需要时间的，所以对于想要线程池创建好就把核心线程准备好，任务一进来就有线程处理，就需要使用线程池的预热功能。ThreadPoolTaskExecutor也提供了实现，prestartCoreThread预热一个核心线程和prestartAllCoreThreads预热全部核心线程，我们在构造函数允许配置线程池预热       
```
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
		
		this.poolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveSecond, TimeUnit.SECONDS, workQueue, threadFactory, handler);
		this.poolExecutor.allowCoreThreadTimeOut(allowCoreThreadTimeOut);
		if (preStartCoreThread == 1) {
			this.poolExecutor.prestartCoreThread();
		} else if (preStartCoreThread > 1) {
			this.poolExecutor.prestartAllCoreThreads();
		}
}
```

**拒绝次数**    
当线程池满后，就会根据maximumPoolSize判断是否继续开启线程处理任务，如果此时生产者还在继续添加任务，而队列依然是满的，那么就会触发线程池的拒绝策略，从上面的图可以看到美团的监控中有一个rejectCount表示拒绝的次数，而jdk的线程池本身没有这个数据，所以只能想办法自己获得。首先定义一个接口，包含一个拒绝次数的计数器，这里使用LongAdder提升并发效率          
```
	public interface EERejectedExecutionHandlerCounter extends RejectedExecutionHandler {

		LongAdder REJECT_COUNTER = new LongAdder();

		default void increment() {
			REJECT_COUNTER.add(1);
		}

		default long get() {
			return REJECT_COUNTER.sum();
		}
	}
```
jdk自带的拒绝策略有CallerRunsPolicy，AbortPolicy，DiscardOldestPolicy，DiscardPolicy，我们可以继承它们，然后再实现上面的接口，就把计数和原来的策略结合起来了，举其中一个例子，只需要在触发拒绝策略的时候计数+1
```
    public static class EECallerRunsPolicy extends ThreadPoolExecutor.CallerRunsPolicy implements EERejectedExecutionHandlerCounter {
		@Override
		public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
			increment();
			super.rejectedExecution(r, e);
		}
	}
```
接着和其它参数一样监控起来    
```
Gauge.builder("enhance.pool.rejectCount", this, s -> handler.get()).tags(tagName, name).register(mr);
```

**动态刷新**     
jdk线程池本身是支持运行过程中修改参数的，从源码可以看到ThreadPoolExecutor有setCorePoolSize,setMaximumPoolSize,setKeepAliveTime，例如maximunPoolSize初始设置为10，若发现队列经常满，触发拒绝策略，那么可以设置到20，ThreadPoolExecutor就可以最大开启20个线程来处理任务，但是没有参数可以设置队列的大小。    
spring的ThreadPoolTaskExecutor也是对jdk ThreadPoolTaskExecutor的封装，也在运行过程支持修改参数，例如通过JMX，但是也没法修改队列的大小。   
```
	/**
	 * Set the ThreadPoolExecutor's maximum pool size.
	 * Default is {@code Integer.MAX_VALUE}.
	 * <p><b>This setting can be modified at runtime, for example through JMX.</b>
	 */
	public void setMaxPoolSize(int maxPoolSize) {
		synchronized (this.poolSizeMonitor) {
			this.maxPoolSize = maxPoolSize;
			if (this.threadPoolExecutor != null) {
				this.threadPoolExecutor.setMaximumPoolSize(maxPoolSize);
			}
		}
	}
```
上图可以看到美团使用了ResizableCapacityLinkedBlockingQueue，但因为没有开源所以不知道它怎么实现的。   
还有一个hippo4j，它是怎么实现的呢，翻看它的源码，确实有发现，原理比较简单，通过自己定义一个类继承LinkedBlockingQueue，然后通过反射修改capacity字段，同时也可以将字段暴露出来。当然也可以直接复制一下LinkedBlockingQueue代码，将capacity属性公开出来，这样就不用反射，效率更高。    
我的实现如下，下面代码最后的if成立后，会反射调用signalNotFull方法，字面意思是发出队列未满的信号，这也是LinkedBlockingQueue的私有方法，它内部会不断的进行take/put元素，如果队列的容量扩大了，比当前队列元素还大，就发出队列未满信号，可以继续put元素。
```
@Slf4j
public class ResizableCapacityLinkedBlockingQueue<E> extends LinkedBlockingQueue<E> {

	@Getter
	private int capacity;

	public ResizableCapacityLinkedBlockingQueue(int capacity) {
		super(capacity);
		this.capacity = capacity;
	}

	public synchronized boolean setCapacity(Integer capacity) {
		boolean successFlag = true;
		try {
			Class superCls = this.getClass().getSuperclass();
			Field capacityField = superCls.getDeclaredField("capacity");
			Field countField = superCls.getDeclaredField("count");
			capacityField.setAccessible(true);
			countField.setAccessible(true);

			int oldCapacity = capacityField.getInt(this);
			capacityField.set(this, capacity);
			capacityField.setAccessible(false);

			AtomicInteger count = (AtomicInteger) countField.get(this);
			countField.setAccessible(false);

			if (capacity > count.get() && count.get() >= oldCapacity) {
				superCls.getDeclaredMethod("signalNotFull").invoke(this);
			}
			this.capacity = capacity;
		} catch (Exception ex) {
			log.error("Dynamic modification of blocking queue size failed.", ex);
			successFlag = false;
		}

		return successFlag;
	}
}
```   

**与apollo集成**     
与apollo配置中心集成，需要监听配置，在配置修改的时候，如果修改的是线程池参数，调用相应方法设置。    
这里的关键是怎么拿到ThreadPoolExecutor呢？EnhanceExecutor是作为bean注册到spring中的，要拿到它里面的线程池有两种做法：   
1.从spring context中把EnhanceExecutor拿出来，例如apollo上配置的是 myPool.coreSize = 10，监听到修改时，需要从spring context看看有没有这个bean，有就修改对应的属性。这种做法可以对EnhanceExecutor bean名称做一些约定，例如bean名称都是：EnhanceExecutor-XXX，这样方便判断配置变化是不是线程池相关的。    
2.将ThreadPoolExecutor保存到一个map，同样配置变动的时候判断一下是不是map里相关的，是的话就调用相应的方法设置。       

apollo上的配置变更不会很大，一个java程序也不会说存在很多个线程池，所以这里判断是很快的，这里以第二种方式代码为例：  
```
@Slf4j
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
} 

public class EnhanceExecutorContainer {

	public static ConcurrentHashMap<String, EnhanceExecutor> MAP = new ConcurrentHashMap<>();
}

@Slf4j
@Component
public class EEApolloListener {

	@ApolloConfigChangeListener
	public void listen(ConfigChangeEvent configChangeEvent) {
		if (EnhanceExecutorContainer.MAP.isEmpty()) {
			return;
		}
		for (Map.Entry<String, EnhanceExecutor> entry : EnhanceExecutorContainer.MAP.entrySet()) {
			//coreSize
			ConfigChange coreSizeChange = configChangeEvent.getChange(entry.getKey() + ".coreSize");
			if (coreSizeChange != null) {
				String oldValue = String.valueOf(entry.getValue().getCorePoolSize());
				log.info(entry.getKey() + " pool change coreSize from {} to {}", oldValue, coreSizeChange.getNewValue());
				executeChange(() -> entry.getValue().setCorePoolSize(Integer.valueOf(coreSizeChange.getNewValue())),
						entry.getKey(), "coreSize", oldValue, coreSizeChange.getNewValue());
			}

			//maximumSize...		
			//keepAliveSecond...			

			//queueCapacity
			ConfigChange queueCapacityChange = configChangeEvent.getChange(entry.getKey() + ".queueCapacity");
			if (queueCapacityChange != null) {
				String oldValue = String.valueOf(entry.getValue().getQueueCapacity());
				log.info(entry.getKey() + " pool change queueCapacity from {} to {}", oldValue, queueCapacityChange.getNewValue());
				executeChange(() -> entry.getValue().setQueueCapacity(Integer.valueOf(queueCapacityChange.getNewValue())),
						entry.getKey(), "queueCapacity", oldValue, queueCapacityChange.getNewValue());
			}
		}
	}

	private void executeChange(Runnable runnable, String poolName, String fieldName, String oldValue, String newValue) {
		try {
			runnable.run();
		} catch (Exception ex) {
			log.error("change {} poll {} from {} to {} error", poolName, fieldName, oldValue, newValue, ex);
		}
	}
}
```

相关源码可以在这里：https://github.com/jmilktea/jtea/tree/master/sample/demo/src/main/java/com/jmilktea/sample/demo/enhance   
**参考**   
[Java线程池实现原理及其在美团业务中的实践](https://tech.meituan.com/2020/04/02/java-pooling-pratice-in-meituan.html)    
[hippo4j](https://github.com/mabaiwan/hippo4j)    
[如何设置线程池参数？美团给出了一个让面试官虎躯一震的回答](https://www.cnblogs.com/thisiswhy/p/12690630.html)    
