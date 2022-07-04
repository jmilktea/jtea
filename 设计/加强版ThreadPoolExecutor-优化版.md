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
public class EnhanceExecutor implements ExecutorService, InitializingBean {

	private String poolName;
	private ThreadPoolExecutor poolExecutor;

	@Autowired
	private EeConfigProperties eeConfigProperties;
	@Autowired
	private MeterRegistry mr;

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
		if (preStartCoreThread == 1) {
			this.poolExecutor.prestartCoreThread();
		} else if (preStartCoreThread == corePoolSize) {
			this.poolExecutor.prestartAllCoreThreads();
		}
	}
}
```

**拒绝次数**    
当线程池满后，就会根据maximumPoolSize判断是否继续开启线程处理任务，如果此时生产者还在继续添加任务，而队列依然是满的，那么就会触发线程池的拒绝策略，从上面的图可以看到美团的监控中有一个rejectCount表示拒绝的次数，而jdk的线程池本身没有这个数据，所以只能想办法自己获得。     
首先定义一个接口继承RejectedExecutionHandler，包含一个获取拒绝次数的方法，通过一个抽象类实现它，在触发拒绝策略的时候计数+1，这里使用LongAdder提升并发效率，而具体的拒绝策略使用的还是jdk自带的。    
```
public interface EeRejectedExecutionHandler extends RejectedExecutionHandler {

	long getRejectCount();

	abstract class EeAbstractPolicy implements EeRejectedExecutionHandler {

		private LongAdder rejectCounter = new LongAdder();

		protected abstract void reject(Runnable r, ThreadPoolExecutor e);

		@Override
		public long getRejectCount() {
			return rejectCounter.sum();
		}

		@Override
		public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
			rejectCounter.add(1);
			reject(r, e);
		}
	}

	class EeCallerRunsPolicy extends EeAbstractPolicy {

		private ThreadPoolExecutor.CallerRunsPolicy rejectExecutor = new ThreadPoolExecutor.CallerRunsPolicy();

		@Override
		protected void reject(Runnable r, ThreadPoolExecutor e) {
			rejectExecutor.rejectedExecution(r, e);
		}
	}
}
```
这样就可以和其它参数一样监控起来    
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
我的实现如下，下面代码最后的if成立后，会反射调用signalNotFull方法，字面意思是发出队列未满的信号，这也是LinkedBlockingQueue的私有方法，它内部会调用notFull一个Condition对象的signal方法去唤醒等待线程，在take和poll取走元素后，也会调用该方法。这里的意思是如果队列的容量扩大了，比队列现有元素还多，就发出队列未满信号，可以继续添加元素了。
```
@Slf4j
public class ResizableCapacityLinkedBlockingQueue<E> extends LinkedBlockingQueue<E> {

	@Getter
	private int capacity;

	public ResizableCapacityLinkedBlockingQueue(int capacity) {
		super(capacity);
		this.capacity = capacity;
	}

	public synchronized boolean setCapacity(int capacity) {
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
				Method signalNotFull = superCls.getDeclaredMethod("signalNotFull");
				signalNotFull.setAccessible(true);
				signalNotFull.invoke(this);
				signalNotFull.setAccessible(false);
			}
			this.capacity = capacity;
		} catch (Exception ex) {
			log.error("dynamic modification of blocking queue size failed.", ex);
			successFlag = false;
		}

		return successFlag;
	}
}
```   

**与apollo集成**     
与apollo配置中心集成，需要监听配置，在配置修改的时候，如果修改的是线程池参数，调用相应方法设置。    
这里的关键是怎么根据配置拿到ThreadPoolExecutor呢？EnhanceExecutor是作为bean注册到spring中的，bean的名称就是线程池的名称，我们可以约定配置的格式是ee.poolname.corePoolSize，这样就可以通过解析配置的key拿到poolname，接着从spring context拿到对应的bean，获取对应的线程池对象。    
同时我们需要在程序启动，线程池初始化完成时候，判断如果apollo上有配置，就要使用apollo上的，上可以看到EnhanceExecutor实现了InitializingBean接口，在afterPropertiesSet方法会根据配置对线程池的属性进行刷新   
```
	@Override
	public void afterPropertiesSet() {
		refreshPool();
		metrics();
		registerShutdown();
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
```
监听apollo配置变更，首先判断变更的key是不是我们关注的，是的话按照约定的格式解析，得到poolName也就是bena name，接着从ApplicationContext获取bean，修改其内部的线程池对应的属性。     
```
@Slf4j
@Component
public class EeApolloListener implements ApplicationContextAware {

	private ApplicationContext applicationContext;

	private static final String eeStartPrefix = "ee.";
	private static final String corePoolSize = "corePoolSize";
	private static final String maximumPoolSize = "maximumPoolSize";
	private static final String keepAliveSecond = "keepAliveSecond";
	private static final String queueCapacity = "queueCapacity";

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@ApolloConfigChangeListener
	public synchronized void listen(ConfigChangeEvent configChangeEvent) {
		for (String changeKey : configChangeEvent.changedKeys()) {
			try {
				if (!changeKey.startsWith(eeStartPrefix)) {
					continue;
				}
				String[] splitNames = changeKey.split("\\.");
				if (splitNames.length != 3) {
					//ee.poolName.fieldName=value
					continue;
				}
				String poolName = splitNames[1];
				EnhanceExecutor pool = applicationContext.getBean(poolName, EnhanceExecutor.class);
				String poolField = splitNames[2];
				String newValue = configChangeEvent.getChange(changeKey).getNewValue();
				if (corePoolSize.equals(poolField)) {
					//corePoolSize
					executeChange(() -> pool.setCorePoolSize(Integer.valueOf(newValue)),
							poolName, corePoolSize, String.valueOf(pool.getCorePoolSize()), newValue);
				} else if (maximumPoolSize.equals(poolField)) {
					//maximumPoolSize
					executeChange(() -> pool.setMaximumPoolSize(Integer.valueOf(newValue)),
							poolName, maximumPoolSize, String.valueOf(pool.getMaximumPoolSize()), newValue);
				} else if (keepAliveSecond.equals(poolField)) {
					//keepAliveSecond
					executeChange(() -> pool.setKeepAliveSecond(Long.valueOf(newValue)),
							poolName, keepAliveSecond, String.valueOf(pool.getKeepAliveSecond()), newValue);
				} else if (queueCapacity.equals(poolField)) {
					//queueCapacity
					executeChange(() -> pool.setQueueCapacity(Integer.valueOf(newValue)),
							poolName, queueCapacity, String.valueOf(pool.getQueueCapacity()), newValue);
				} else {
					log.warn("pool change {} fail,not support field");
				}
			} catch (BeansException ex) {
				log.warn("pool change {} fail,the bean of EnhanceExecutor could not be found", changeKey, ex);
			} catch (Exception ex) {
				log.error("pool change {} error", changeKey, ex);
			}
		}
	}

	private void executeChange(Runnable runnable, String poolName, String fieldName, String oldValue, String newValue) {
		log.info("{} change {} from {} to {}", poolName, fieldName, oldValue, newValue);
		try {
			runnable.run();
		} catch (Exception ex) {
			log.error("{} change {} from {} to {} error", poolName, fieldName, oldValue, newValue, ex);
		}
	}
}

```

相关源码可以在这里：https://github.com/jmilktea/jtea/tree/master/sample/demo/src/main/java/com/jmilktea/sample/demo/enhance   

**总结一下目前EnhanceExecutor的功能特性**      
- 支持线程池相关参数指标监控   
- 服务优雅下线线程池中断处理    
- 支持动态修改线程池参数     
- 支持作为spring bean注入，使用spring Async异步注解    
- 封装/兼容jdk ThreadPoolTaskExecutor，支持统计任务处理情况    

**参考**   
[Java线程池实现原理及其在美团业务中的实践](https://tech.meituan.com/2020/04/02/java-pooling-pratice-in-meituan.html)    
[hippo4j](https://github.com/mabaiwan/hippo4j)    
[如何设置线程池参数？美团给出了一个让面试官虎躯一震的回答](https://www.cnblogs.com/thisiswhy/p/12690630.html)    
