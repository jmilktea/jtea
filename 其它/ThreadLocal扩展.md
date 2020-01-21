## 简介
ThreadLocal用于存储当前线程的信息，可以把它看做线程的context，每个线程都有自己的context，互不影响，在多线程模型下，通过为每个线程维护一份本地信息，可以避免资源竞争带来的影响。ThreadLocal有很多应用场景，如日志收集，多数据切换等。ThreadLocal内部维护一个ThreadLocalMap，真正存储数据的在这个Map中，它与Thread的关系如图  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%85%B6%E5%AE%83/images/ThreadLocalRef.png)  
但同时ThreadLocal也有一些限制，例如在当前线程下创建子线程，使用线程池，由于脱离了当前线程所以ThreadLocal生效。这里介绍另外两个扩展，InheritableThreadLocal和[阿里的TransmittableThreadLocal](https://github.com/alibaba/transmittable-thread-local)，它们都是在ThreadLocal上做扩展，InheriatableThreadLocal解决了父子线程问题，它在创建线程的时候会copy一份数据到子线程，实现数据传递，但没法解决使用线程池的问题，使用线程池时线程将被复用，这里通过阿里的TransmittableThreadLocal实现，它继承了InheritableThreadLocal。详细参见代码

## 代码
```
@SpringBootTest
class ThreadLocalTests {

    private static ThreadLocal threadLocal = new ThreadLocal();
    
    private static InheritableThreadLocal inheritableThreadLocal = new InheritableThreadLocal();

    private static ExecutorService executorService = Executors.newFixedThreadPool(1);

    private static TransmittableThreadLocal<String> transmittableThreadLocal = new TransmittableThreadLocal<String>();

    private static ExecutorService transmittableExeService =  TtlExecutors.getTtlExecutorService(executorService);

    @Test
    public void testThreadLocal() throws InterruptedException {
        threadLocal.set("test");
        new Thread(() -> {
            //子线程，ThreadLocal已经无效
            System.out.println(Thread.currentThread().getName() + ":" + threadLocal.get());
        }).start();
        System.out.println(Thread.currentThread().getName() + ":" + threadLocal.get());
        Thread.sleep(2000);
    }

    @Test
    public void testInheritableThreadLocal() throws InterruptedException {
        inheritableThreadLocal.set("test");
        new Thread(() -> {
            //子线程，InheritableThreadLocal有效
            System.out.println(Thread.currentThread().getName() + ":" + inheritableThreadLocal.get());
        }).start();
        System.out.println(Thread.currentThread().getName() + ":" + inheritableThreadLocal.get());
        Thread.sleep(2000);
    }

    @Test
    public void testInheritableThreadLocal4ThreadPool() throws InterruptedException {
        inheritableThreadLocal.set("test");
        System.out.println(Thread.currentThread().getName() + ":" + inheritableThreadLocal.get());
        executorService.submit(() -> {
            //输出test
            System.out.println(Thread.currentThread().getName() + ":" + inheritableThreadLocal.get());
        });
        //重新设置为test2
        inheritableThreadLocal.set("test2");
        executorService.submit(() -> {
            //还是输出test，因为线程池的线程被复用
            System.out.println(Thread.currentThread().getName() + ":" + inheritableThreadLocal.get());
        });
        Thread.sleep(2000);
    }

    @Test
    public void testTransmittableThreadLocal4ThreadPool() throws InterruptedException {
        transmittableThreadLocal.set("test");
        System.out.println(Thread.currentThread().getName() + ":" + transmittableThreadLocal.get());
        transmittableExeService.submit(() -> {
            //输出test
            System.out.println(Thread.currentThread().getName() + ":" + transmittableThreadLocal.get());
        });
        //重新设置为test2
        transmittableThreadLocal.set("test2");
        transmittableExeService.submit(() -> {
            //还是输出test2
            System.out.println(Thread.currentThread().getName() + ":" + transmittableThreadLocal.get());
        });
        Thread.sleep(2000);
    }
}

```
