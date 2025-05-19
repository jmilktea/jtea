# 现象
我们生产最近有个服务偶尔会挂掉，接口报错"**connection reset by peer**"，上服务器curl也是同样报错，意思连接被server拒绝了。  

通过dump以及日志分析，我们已经知道了问题代码所在，就是使用easyexcel上传、解析文件，开发同学没有做分页，导致内存溢出。这点在easyexcel文档也有提到：[参见](https://easyexcel.opensource.alibaba.com/docs/current/quickstart/read)。    

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-1.png)


内存溢出后，触发频繁的full gc，由于gc很难有效回收内存，所以程序抛出了OutOfMemoryError，原因是：Java heap space。    

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-2.png)

关于OOM的异常原因，我们也需要知道，有如下几种：  

**Java heap space**    
内存无法分配新的对象。典型的场景是：内存不足，内存泄漏，分配的对象过大。  

**GC Overhead limit exceeded**  
gc回收异常，多次发生了98%的时间用于gc，但只回收2%的内存。典型的场景是：内存不足，内存泄漏。  

**Metaspace**  
元空间不足。典型的场景是：元空间设置太小，程序异常创建过多的Class。  

**Direct buffer memory**  
直接内存不足。典型的场景是：直接内存设置太小，直接内存泄漏。  

**Unable to create new native thread**  
无法创建新的线程。典型的场景是：系统ulimit -u设置太小。  

**Requested array size exceeds VM limi**   
数组大小太大。典型的场景是：new ClassA[Long.MAX_VALUE]

**CodeCache**   
jit编译缓存溢出。典型的场景是jit缓存设置过小。  

注意，我们上面提到问题的是内存溢出，而不是内存泄漏，两者有本质的区别。  
**内存溢出**，通常是分配大对象，应用内存不足，通常分配多点空间就可以解决问题，而且所占的内存用完还是可以被回收的。  
**内存泄漏**，则是程序有问题，内存是无法被回收的，分配再多的空间也都会被慢慢消耗完。  

打个比方，内存溢出只是人长得不好看，不是坏人，内存泄漏则长得不好看，也是坏人。当然两者我们都需要对其进行优化。        

通过我们的观察也发现确实如此，经过一段时间后，文件解析数据处理完，内存就被回收了，也没有full gc了。  

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-3.png)

但问题来了，此时应用http请求还是继续报错的，依然是"**connection reset by peer**"。这点就不好理解了，应用恢复了，为什么tomcat没有恢复，tomcat线程此时在做什么？从日志也看不到tomcat相关错误，**tomcat假死了**。  

从监控上看，挂掉之前tomcat的请求线程数和连接数没有什么波动。  

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-4.png)

**我们的两个核心问题是：**

- 什么是"connection reset by peer"？  
- tomcat线程此时处于什么状态？    

# 连接报错    
我们搜索源码，并没有抛出"**connection reset by peer**"的代码，也就是可能是jvm层面的抛出，或者系统层面的抛出。 

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-5.png)

既然是跟connection相关，那我们就用netstat命令看下当前进程的连接情况：  
```
netstat -tlnp | grep 8100      
netstat -anp | grep 8100  
```

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-6.png)


从输出可以发现，有一个特殊的101，我们用正常的进程看一般都是0。  

这个参数叫：**backlog**，表示连接等待队列的长度，对应tomcat的**acceptCount**参数，默认是100。        

当连接超过这个值时，就会报"**connection reset by peer**"，我们当前是101，所以新来的请求就被拒绝了。      

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-7.png)

# tomcat线程模型  
对于第二个问题，tomcat线程正在干什么。一般我们可以通过jstack pid导出线程堆栈分析，不过当我们的服务运行一段时间，例如好几天后，执行jstack，jmap都会报错，似乎是某些信息被系统清除掉，这点我还找到根本原因，如果你知道答案请告知我一下。    

幸好有arthas，我们可以通过thread命令，查看线程和其堆栈信息。  
```
thread -n 10  #top 10 cpu
thread 1 #展示线程1的堆栈
thread --all  #展示所有线程  
thread --all | grep http #展示http线程  
```
![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-8.png)
 
我们可以看到，tomcat的10个核心线程还是在的，且处于waitting状态。  
处于waitting状态是因为它在等任务执行，从堆栈可以看出是阻塞在TaskQueue.take方法，org.apache.tomcat.util.threads.TaskQueue是tomcat中的LinkedBlockingQueue，是生产者-消费者模型，take方法阻塞表示当前队列是空的，没有任务需要执行，一旦有任务放入TaskQueue，take方法就会唤醒，进入Runnable状态。 

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-9.png)

这点就很奇怪了，前面说连接队列都满了，但tomcat任务队列确是空的，执行线程都处于等待任务状态，一边满载一边空闲。    

要搞清楚这个问题，需要我们对tomcat线程模型有所了解。tomcat支持几种IO模型，**BIO、NIO、AIO(NIO2)、APR**，我们可以通过**server.tomcat.protocol**参数进行设置，默认用的是NIO，NIO是一种同步非阻塞IO。    

> NIO的核心目的是可以用少量线程处理大量连接，在linux用select/poll/epoll实现。  

> NIO在很多中间件都有应用，kafka,redis,rocketmq,gateway等等，可以说涉及到网络处理的都离不开NIO。  

> AIO是真正的异步IO，但Linux对其支持不够完善，且NIO已经足够高效，所以NIO用得最多。  

## reactor模型  
如何更好的实现NIO是个问题，就好像我们实现某个功能要用到设计模式一样，reactor就是NIO一种实现模式。doug lee在[scalable io in java](https://gee.cs.oswego.edu/dl/cpjslides/nio.pdf)总结了3种模型：  

**单reactor单线程**   
整个过程由一个线程完成，包括创建连接，读写数据，业务处理。redis 6.0以前的版本就是这种模式，实现起来简单，没有线程切换，加锁的开销。缺点是单线程不能发挥多核cpu的优势，如果有一个业务处理阻塞了，那么整个服务都会阻塞。    

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-10.png)

**单reactor多线程**     
接收连接(accept)和IO读写还是由一个线程完成，但业务处理会提交给业务线程池，业务处理不会阻塞整个服务。  

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-11.png)

**多reactor多线程**      
接收连接由一个main reactor处理，建立连接后将其注册到sub reactor上，每个sub reactor都是单reactor多线程模式。    

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-12.png)

## tomcat的实现   
tomcat的NIO由3种线程实现，分别是：**Acceptor线程、Poller线程、请求处理线程。**     

对于请求处理线程池我们比较熟悉，常用的两个参数：  

```
server.tomcat.minSpareThreads = 10    
server.tomcat.maxThreads = 200  
```

对应到reactor模型，可以看成它是一种多reactor多线程模型，Acceptor线程负责建立连接，然后将建立好的连接注册到Poller，由Poller进行读写。Poller读写后创建请求，将其交给请求处理线程池。     
Acceptor和Poller线程对应的run方法都是一个死循环，源源不断的接收连接、读写连接。  

从reactor模型上看，在多核cpu下，多reactor多线程模型可以获得更高的效率，但tomcat10以下默认只能有一个Acceptor和一个Poller线程。  

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-13.png)

## 源码分析

源码位置：org.apache.tomcat.util.net.NioEndpoint#startInternal，开启Acceptor线程和Poller线程：

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-14.png)

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-15.png)

源码位置：org.apache.tomcat.util.net.Acceptor#run，while循环，建立连接：

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-16.png)

源码位置：org.apache.tomcat.util.net.NioEndpoint.Poller#run，while循环，读取数据：  

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-17.png)

源码位置：org.apache.tomcat.util.net.AbstractEndpoint#processSocket，将封装好的请求交给请求线程池处理：  

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-18.png)

> 关于tomcat线程池有一个点要注意，它和jdk不一样的是，它是先开启核心线程，当任务超过核心线程数，就继续开启至最大线程数，如果还超过才进入等待队列。      

# 水落石出  
通过上面的分析，让我们回到问题出现时的这张图  

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-8.png)

可以看到，**Acceptor和Poller线程消失了！**  

这样我们现象就很好解释了，Acceptor没有拿新的连接来处理了，此时连接在系统层面积压，tomcat请求处理线程空闲。    

我们重启后再执行一下thread命令，正常的是：    

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-19.png)

从Acceptor源码上看，它捕获了异常，但对于OOM，选择重新抛出，Acceptor线程就中断了，可见OOM对于tomcat来说是个**致命异常**，一旦程序有此类报错，需要优化，否则可能导致整个服务异常。   
且Acceptor和Poller线程抛出这个位置在打印日志之前，所以也看不到错误日志，这点似乎不太好，但最新的tomcat版本也是保持如此。    

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-20.png)

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/tomcat-21.png)

如果要获得这个日志，我们也可以通过Thread的全局异常来捕获：    

```
Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
    if (t.getName().equals("http-nio-8100-Acceptor")) {
        log.error("tomcat Acceptor error", t);
    }
});
```

# 总结    
OOM异常对于tomcat服务来说是致命的，发现即需要处理。    
对于内存泄漏来说，留有时间给我们dump内存分析。但对于内存溢出来说，由于其会回收，可能在某个时间OOM，顺便把tomcat打挂了，然后就回收了，此时我们去dump也未必有用，所以-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath 参数是很有必要需要加上的。    
