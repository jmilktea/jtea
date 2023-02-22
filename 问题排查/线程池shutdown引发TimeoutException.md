## 问题描述    
问题的出现是在每次发版，服务准备下线的时候相关服务都会报错，报错的位置是在将任务submit提交给线程池，使用Future.get()引发的TimeoutException，错误日志会打印下面的"error"。伪代码如下：
```
List<Future<Result<List<InfoVO>>>> futures = new ArrayList<>();
lists.forEach(item -> {	
	futures.add(enhanceExecutor.submit(() -> feignClient.getTimeList(ids)));	
);
futures.forEach(
	item -> {
		try {
			Result<List<InfoVO>> result = item.get(10, TimeUnit.SECONDS);			
		} catch (InterruptedException | TimeoutException | ExecutionException e) {
			log.error("error", e);
	    }
	}
);
```
代码逻辑非常简单，就是将一个Feign接口的调用提交给线程池去并发执行，最终通过Feture.get()同步获取结果，最多等待10s。     
线程池的配置参数是：核心线程数为16，最大线程数为32，队列为100，解决策略为CallerRunsPolicy，意为当线程无法处理任务时，任务交还给调用线程执行。

## 问题分析    
问题分析的开始走了一些弯路，因为Timeout异常给人最直观的感受就是接口超时了，加上这个接口也确实偶尔超时，所以我们用arthas分析了一下接口执行时间，发现接口并不慢，结合上面的线程池参数，基本不会出现超时。同时通过grafana上的监控，分析接口的qps和执行时间，基本可以排除是接口超时这一点。     

后来开始怀疑是不是对方服务也在下线，因为我们几个服务多数时候会一起更新，从而导致Feign出现异常，还使用了resilience4j，它里面也有超时和线程池，会不会是它在这种场景下出现问题导致。   
这里又绕了一个圈，通过各种google,github,chatgpt后，没有发现相关资料。这后来也给我一个警示就是，在怀疑相关组件之前，要先排查完自己的代码，没有头绪时不要一下子钻进去。    

后来结合日志的时间线，重新梳理。上面的线程池是我们自己封装的线程池，支持监控、apollo动态修改线程池参数，日志跟踪traceId打印，执行任务统计，服务下线线程退出等功能，这很像[美团技术团队](https://tech.meituan.com/2020/04/02/java-pooling-pratice-in-meituan.html)提到的线程池，不过我们基于自己的需求进行封装，使用起来更简单、轻量。     
在[服务优雅下线](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/%E6%9C%8D%E5%8A%A1%E4%BC%98%E9%9B%85%E4%B8%8B%E7%BA%BF.md)这篇，我们写到   
![image](1)    

在服务下线前该线程池会响应一个event bus消息，然后执行线程池的shutdown方法，本意是服务下线时，线程池不再接收新的任务，并触发拒绝策略。那会不会是这里出现问题呢？    
结合上面的代码，当线程池shutdown后，执行CallerRunsPolicy策略，再submit应该就会阻塞。这就是我们平时理解的，当队列满了，就继续开启线程至maximumPoolSize，如果线程数已经达到maximumPoolSize，并且队列也满了，此时就触发解决策略。    
如下代码，当第三次submit的时候就阻塞了，符合上面说的情况。
```
	    ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.MINUTES, new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.CallerRunsPolicy());
		threadPoolExecutor.submit(() -> {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
			}
		});
		threadPoolExecutor.submit(() -> {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
			}
		});
		//到这里就阻塞了
		threadPoolExecutor.submit(() -> {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
			}
		});
```

那如何期间shutdown了呢？按照网上的很多介绍，**如果线程池shutdown了，再提交任务，就触发拒绝策略。这句话本身没有错，但也没有完全对，坑就在这里。** 如果你执行下面的代码，会发现和上面是不一样的，第三个submit不会阻塞了。         
```
	    ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.MINUTES, new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.CallerRunsPolicy());
		threadPoolExecutor.submit(() -> {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
			}
		});
		threadPoolExecutor.submit(() -> {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
			}
		});
        
        //加了这一行
        threadPoolExecutor.shutdown();

		//这里不会阻塞了...
		threadPoolExecutor.submit(() -> {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
			}
		});
```
为什么会这样呢，我们跟踪下源码，发现它确实会走到拒绝策略，但在CallerRunsPolicy拒绝策略里面有一个判断，如果线程池不是shutdown的，就直接调用Runnable的run方法，这里使用的是调用者线程，所以调用者线程会阻塞，如果线程池是shutdown的，就什么也不做，相当于任务丢弃了。    

按照这个说法，如果我在最后使用Future接收一下submit的返回值，然后调用Future.get方法，会发生什么？
```
	    ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.MINUTES, new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.CallerRunsPolicy());
		threadPoolExecutor.submit(() -> {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
			}
		});
		threadPoolExecutor.submit(() -> {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
			}
		});
        
        //加了这一行
		threadPoolExecutor.shutdown();

		//这里不会阻塞了...
		Future future = threadPoolExecutor.submit(() -> {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
			}
		});
        
        //这里会发生什么？
		future.get(10, TimeUnit.SECONDS);
```

结果是超时了，报了TimeoutException，如下图：  
![image](3)    

我们的问题得以复现，但future.get为什么会超时呢？正常情况下，它是实现阻塞调用线程的，又是如何在线程拿到执行结果时返回执行的，这就需要我们对Future的原理有所理解了。

## Future 原理
Future字面意思是未来的意思，很符合语意。当我们使用异步执行任务的时候，在未来某个时刻想知道任务执行是否完成、获取任务执行结果，甚至取消任务执行，就可以使用Future。   
Future是一个接口，FutureTask是它的一个实现类，同时实现了Future和Runnable接口，也就是说FutureTask即可以作为Runnable执行，也可以通过它拿到结果。    
ThreadPollExecutor.submit的源码如下，newTaskFor就是创建一个FutureTask。    
![image](4)

假如任务提交后还没执行完，我们看它是如何实现阻塞的，带超时时间的get()方法源码如下：
![image](5)    
代码中判断如果state > COMPLETING，就直接调用report，也就是直接返回。state是个私有成员遍历，它可能有以下值，大于1表示是任务终态直接返回。   
![image](6)    
否则就进入awaitDone()方法，代码如下：  
![image](7)    
该方法是个无条件for循环，但它绝不是通过消耗cpu不断检查某个状态来获取结果，这样效率太低了。   
按照“正常”调用(我们只考虑最简单场景，不要受一些异常或不重要的分支干扰，以免越陷越深)，这个for循环会进入3次，分别就是上图打断点的3个位置。   
第一个位置会创建一个WaitNode节点，WaitNode保护一个Thread和一个next，很明显它会构成一个链表。  
![image](8)
第二个位置会尝试用CAS的方式将它将这个节点添加到链表头部，如果添加失败，就会继续for循环，一直到添加成功。添加成功就会进入第三个断点位置。   
第三个位置会调用LockSupport.parkNanos(this, nanos)，阻塞当前线程。

**这里为什么是一个链表呢？** 原因很简单，我们将任务提交后，可以在多个线程等这个任务的结果，也就是在多个线程调用get()方法，那么每一次就会创建一个WaitNode，并形成一个链表。   


ok，知道Future.get()怎么实现阻塞的，我们看下当任务执行完，它是如何恢复并拿到结果的。    
回到上面线程池的submit方法，FutureTask作为一个Runnable传递给线程池execute，那么最终就会执行它的run()方法。     
我们还是主要看“正常”执行的流程，执行完会走到set方法，做两个事情：   
1.将state状态设置为NOMAL，表示任务正常执行完成。    
2.执行finishCompletion方法，遍历waiters链表所有节点，每个节点对应一个线程，将线程取出来，执行LockSupport.unpark(t)恢复线程执行。    
![image](9)   
![image](10)    

## 总结   
通过源码分析我们知道，当调用Future.get()线程阻塞时，它的恢复是靠FutureTask.run()恢复的，也就是我们提交的任务被执行后恢复。   
当我们线程shutdown后，再submit任务确实会触发拒绝策略，但CallerRunsPolicy会判断线程池状态是否是shutdown，如果不是，就直接调用Runnable.run()方法，相当于在调用线程执行。如果是shutdown状态就什么都不做，问题就出在这里，我们是要依靠它的执行来恢复阻塞的，现在什么都不做，就无法恢复了。同样的DiscardPolicy，DiscardOldestPolicy也会有这个问题，AbortPolicy是直接抛出异常，调用线程在submit就抛异常了，走不到Future.get()方法。     
但java为什么要这么做呢？这个拒绝策略的本意就是使用调用者线程执行，但这种情况下却将任务丢弃了。我看了jdk17的源码，这个逻辑并没有改变，也就是有一定的合理性。    
线程池关闭当线程池已经shutdown，则意味着其不能再接收新任务，如果它shutdown了还使用调用线程执行，其实本质上还是在接收新任务，这违背了线程池规定的shutdown以后不再接收新任务的语意。    
总之，在使用shutdown的时候需要注意这个问题，例如我们的场景应该是在触发服务下线等待请求都处理完成后再shutdown，而不是一开始就shutdown，这样有一些请求还在处理中就会出现问题。或者在保证服务下线等待事件内任务都能处理完，就干脆不要shutdown了，让调用者自己去保证这个事情。    