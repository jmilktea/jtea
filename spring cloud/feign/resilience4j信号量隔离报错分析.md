# 起因
当前，我们某些应用开启的线程数极高，存在优化空间。
通过jstack分析，大量的线程是bulkhead-*的线程，也就是resilience-feign相关线程。

![image](1)   
![image](2)    
![image](3)    

相关的配置在resilience.thread-pool-bulkhead，默认我们配置了非常大的线程数。   
且核心线程数是200个，thread-pool-bulkhead使用了jdk的线程池，当我们提交任务的时候，若线程池线程小于核心线程，会一直创建线程达到核心线程数，且核心线程不会被回收。   
意思是，即使要执行的任务并发很低，偶尔几个，也会一直创建核心线程，时间积累达到200个，不会销毁。    

![image](4)   

**大量线程有以下问题：**      
1.占用过多内存，特别我们有些环境内存小时，不友好。   
2.影响部署，有些环境我们使用混合部署应用，内存分配困难。  
3.影响gc，线程作为gc root，会影响gc效率。  
4.线程切换，影响执行效率。  
5.运维反馈我们应用线程数占pid_max比例过高。   


**为什么要隔离？**   
在解决问题前，我们首先要知道为什么要隔离？答案是隔离是为了避免相互影响，不被一个异常下游拖垮整个应用。   
例如，我们有A,B,C三个下游，使用线程池隔离，当A服务很慢时，它也是隔离在自己的线程池，不会影响B,C和外部调用的http线程。    
但在我们的场景，是无效的。因为我们的http和feign都是同步调用，没有用Future，即使切换到隔离线程执行，外部也是要同步等待执行完，多了一次线程切换，没有任何收益。   
有效的隔离：   

![image](5)   

无效的隔离：

![image](6)   

可以看到，有效的隔离，http线程会一直被使用，不会被隔离线程池影响。无效的隔离http线程阻塞在等待结果返回，当下游有问题时，http线程还是被影响了。   
需要注意的是，这只是我们feign的使用方式，resilience的隔离不局限在feign的调用，也可以用编程的方式，用在数据库调用，本地方法调用等。   

# 切换隔离方式
基于上述原因，我打算将线程池隔离切换到更轻量的信号量隔离。配置如下：   
```
spring:
  cloud:
    circuitbreaker:
      resilience4j:
        enableSemaphoreDefaultBulkhead: true 


resilience4j:
  bulkhead:
    configs:
      default:
        maxConcurrentCalls: 200
```

这样配置，每个feign允许最大并发为200，超过时不等待，会报BulkheadFullException。   
修改上线后很快出现大量报错，其中有超时的报错，有熔断的报错，有服务间调用的报错......     
由于报错信息多且杂，无法立刻分析出问题，为了不影响业务，立刻回滚配置，报错恢复。    

![image](7)     

# 问题分析
虽然报错的原因很多，但经验告诉我们这个超时报错是首要分析的。可是切换到信号量，为什么会超时报错呢？逐步分析以下原因：    

1.恰好那段时间就有超时
基本排除，概率很低，且报超时的方法有很多个。

2.并发太高，获取信号量等待太久
很有可能。但按照配置，没有配置maxWaitDuration，超过maxConcurrentCalls的现在应该是报BulkheadFullException，在日志中找不到。

3.其它原因
只能通过源码分析，压测来复现。

结合监控分析，可以看到当时有大量线程block，这大概就是超时的根本原因，可惜当时为了快速恢复服务，没有dump线程堆栈。   
虽然前面feign调用报了TimeoutException，但它是通过CompleteFuture抛出来的，实际请求还没有发起，而是一直阻塞在某个位置，最终导致CompleteFuture.get的超时，而非接口响应的超时。    

![image](8)  

**本地压测**   
jmeter 50个线程调用A服务个接口，这2个接口调用B服务的2个接口，当B服务员接口响应在100ms时，表现正常。    
将其中B服务其中一个接口响应设置为1-2s，模拟生产一些慢接口，报错复现。如下：    

![image](9)  

**源码分析**   
核心代码在org.springframework.cloud.circuitbreaker.resilience4j.Resilience4jBulkheadProvider，方法如下。    
当使用信号量隔离是，会通过 CompletableFuture.supplyAsync(supplier) 来执行我们的feign调用。   

![image](10)  

根本原因，CompletableFuture默认会使用ForkJoinPool.commonPool()，这是一个默认线程池，线程数默认是cpu核数 - 1。 
生产上服务是4C，也就是只有3个线程来处理所有的请求，当其中有接口慢时，很容易就出现阻塞，最终等到超时，报TimeoutException。    

# 总结
使用resilience4j信号量隔离，适合一些本地方法或RT特别短的场景，并不适合feign调用的场景。gpt的建议是：   
![image](11)  

我们解决方案有：
1. 使用semaphore + 配置ForkJoinPool线程池（不推荐）
通过 -Djava.util.concurrent.ForkJoinPool.common.parallelism = 100

2. 使用threadpool + 降低corePoolSize（推荐）
```
thread-pool-bulkhead:
  configs:
    default:
      maxThreadPoolSize: 200
      coreThreadPoolSize: 20
      keepAliveDuration: 60s
```

从spring-cloud-circuitbreaker 最新源码上看，依然保持这种写法，使用时需要谨慎。

![image](12)  




