wait/notify是多线程一种等待通知机制，在并发编程里非常常见，这是一个较为基础的面试题，可以有如下问法：   
- java中线程有哪些状态，以及它们是如何流转的   
- wait/notify为什么设计为Object方法，为什么需要写在synchronized同步块中，不在会怎么样       
- wait/notify和LockSupport中的park/unpark有什么相同点和不同点    
- jdk中有哪些类或方法使用了这个实现或思想          

首先我们来看下java中线程的状态有哪些，总共有如下6种，它们定义在Thread.State这个枚举中：   
- NEW 新建状态     
如new Thread()，所创建的线程就处于NEW状态，该线程还没有进入到等待执行状态，更不会被cpu执行    

- RUNNABLE 可运行状态    
RUNNABLE可以分为等待运行和正在运行两种，等待运行是等CPU真正执行的过程，现代cpu都是基于时间片执行线程的，我们开始线程执行后也不一定能立马执行到，还需要等cpu分配时间片。进入等待运行阶段有如下方式：   
    - 调用start方法，线程不一定立马得到cpu执行时间片，会进入等待运行阶段
    - 调用yield方法，正在运行的线程调用让步方法，会让线程重新进入等待运行阶段，它可以又马上得到时间片运行    
    - sleep时间过期
    - 线程获取到锁资源，包括被notify唤醒后获取到锁资源   
    
- BLOCKED 阻塞状态    
当线程竞争锁资源失败时就会进入阻塞状态，需要等待获取到锁资源才会进入RUNNABLE状态        

- WAITING 等待状态    
调用Object.wait或者LockSupport.part都会让线程进入等待状态     
**等待状态与阻塞状态从代码层面上看都会暂停线程的执行，那么它们之前有什么区别呢？**    
等待状态是已经获取到锁，主动让出锁，而阻塞状态是获取不到锁而被动退出。此外，等待状态需要主动notify通知，才能重新竞争锁，没有收到通知会无限等待，阻塞状态则不需要。     
**那么Object.wait与LockSupport.park又有什么区别呢？**     
使用wait/notify方法的前提是需要锁，也就是需要synchronized修饰的方法或代码块内，否则会报错。LockSupport在java.util.concurrent包下，park也有对应的unpark方法，但它不需要锁就可以实现线程的暂停和恢复。notify只能随机唤醒一个线程，或使用notifyAll唤醒等待的所有线程，而LockSupport.unpark的入参是Thread参数，可以唤醒指定的线程，相比之下比较灵活。此外，如果在调用wait前先调用了notify会导致线程无限等待，而先调用unpark再调用park，park方法会不起作用，不会无限等待。wait/notify搭配synchronized使用，而LockSupport在AQS有很多运用，如ReentrantLock，这两者很像问synchronized关键字和Lock接口的关系。        

- TIMED_WAITING 超时等待状态     
也是一种等待状态，有一个超时时间，超过就不等待了。像sleep，wait指定超时时间，LockSupport.parkUntil指定超时时间，都会进入超时等待状态    

- TERMINATED 结束状态    
当线程运行完就是结束状态，需要知道的是结束状态的线程是不能再次运行的，例如调用start方法进行RUNNABLE状态，待cpu执行完任务后，线程就会进入结束状态，若再次调用start方法，就会抛出异常。如下代码会抛出IllgegalThreadStateException异常：   
```
	@Test
	public void testRunThreadTwice() throws InterruptedException {
		Thread thread = new Thread(() -> {
			System.out.println("123");
		});
		thread.start();
		Thread.sleep(1000);
		System.out.println(thread.getState());
		thread.start();
	}
```

借用网上的一张图总结一下上面6种状态的关系     
![image](https://github.com/jmilktea/jtea/blob/master/%E9%9D%A2%E8%AF%95/images/wait-notify-1.png)     

wait/notify跟锁是紧密相关的，而java中的锁又是设计在对象头标志位的，任何对象都可以当做锁来使用，多线程就是竞争获取这个对象上的锁，相当于把使用者(线程)和资源(锁对象)分开，资源可以被多个线程使用，而线程也可以使用多个资源。wait/notify方法都是跟锁相关，所以设计在Obejct类中是合理的，这样当我们调用wait方法就知道在释放哪个锁资源，并把对应挂起的线程和它关联起来，notify/notifyAll也同理，可以通过这个锁对象找到一个或全部挂起的线程恢复运行。    
上面我们也提到wait/notify是搭配synchronized使用，那么如果不搭配呢，我们直接写    
```
private final Object lock = new Object();
lock.wait();   
```
就会抛出IllegalMonitorStateException，从wait方法的注释可以看出，如果当前线程没有获得该对象的锁，调用该方法就会抛出这个异常，这一点编译器确没有提示我们，这可能导致运行时报错。    
```
    /**   
     * @throws  IllegalMonitorStateException  if the current thread is not
     *          the owner of the object's monitor.
    */
    public final void wait() throws InterruptedException {
        wait(0);
    }
```

**那么在jdk中哪些地方用到这个机制呢**    
**Thread.join**的作用是等待线程执行结束，例如A线程想要等待B线程执行结束再执行，就可以A线程中使用bthread.join()等待B线程执行结束，例如面试有时候会问如何让多个线程顺序打印问题，join的内部就使用了wait/notify机制，源码如下：    
```
public final void join() throws InterruptedException {
    join(0);
}

public final synchronized void join(long millis) throws InterruptedException {
    long base = System.currentTimeMillis();
    long now = 0;

    if (millis < 0) {
        throw new IllegalArgumentException("timeout value is negative");
    }

    if (millis == 0) {
        while (isAlive()) {
            wait(0);
        }
    } else {
        while (isAlive()) {
            long delay = millis - now;
            if (delay <= 0) {
                break;
            }
            wait(delay);
            now = System.currentTimeMillis() - base;
        }
    }
}
```
可以看到该方法使用了synchronized修饰，需要注意的是我们是在A线程中执行bthread.join，synchronized修饰在方法上，锁对象即是当前实例对象，也就是bthread对象，isAlive和wait都是针对current thread也就是A线程，所以A线程会进入等待状态，当B线程执行结束,jvm会在底层调用锁对象bthread的notify方法，进而让在锁对象等待的A线程恢复执行，这里看起来有点绕，结合上面说到的线程和锁对象理解。   

这种等待通知机制在队列中也有运用，例如BlockingQueue的生产者消费者模型，假设队列容量是10，当队列满了，生产者需要阻塞，如果消费者消费了，队列不满了，通知生产者继续生产。同理当队列空了，消费者需要阻塞，如果生产者生产了，队列不空了，通知消费者继续消费。如图：   
![image](https://github.com/jmilktea/jtea/blob/master/%E9%9D%A2%E8%AF%95/images/wait-notify-2.png)      

队列的这种机制也可以用wait/notify来实现，思想上是一样的，不过jdk在java层面提供了像AQS这样的并发编程工具，在使用多线程时多了一种选择，就如同synchronized和Lock接口，在java层面的接口提供了更加灵活的实现，性能也更好。以ArrayBlockingQueue为例，我们看下put和take方法的实现。    
```
    public void put(E e) throws InterruptedException {
        checkNotNull(e);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (count == items.length)
                notFull.await();
            enqueue(e);
        } finally {
            lock.unlock();
        }
    }

    private void enqueue(E x) {
	//...
        notEmpty.signal();
    }
```
put方法也是先加锁，这里用的就不是synchronized了，是ReentrantLock。接着判断如果集合的元素数量已经等于队列容量就表示满了，此时会调用notFull.await()，notFull是个Condition对象，在它内部会使用LockSupport.park将线程挂起。如果没满，enqueue会将元素放入队列，然后调用notEmpty.signal()，notEmpty也是个Condition对象，signal内部会调用LockSupport.unpark唤醒线程，相当于notify唤醒消费者线程。关于Condition对象看它的注释就非常明白了，它在代码层面实现了Object锁相关的wait/notify/notifyAll方法，与Lock接口配合适用，可以替换synchronized的相关语句。      
```
 * {@code Condition} factors out the {@code Object} monitor
 * methods ({@link Object#wait() wait}, {@link Object#notify notify}
 * and {@link Object#notifyAll notifyAll}) into distinct objects to
 * give the effect of having multiple wait-sets per object, by
 * combining them with the use of arbitrary {@link Lock} implementations.
 * Where a {@code Lock} replaces the use of {@code synchronized} methods
 * and statements, a {@code Condition} replaces the use of the Object
 * monitor methods.
 *
```
take方法是相反的过程，判断如果数量为0，就调用notEmpty.await挂起消费线程，否则dequeue会调用notFull.signal()唤醒生产线程继续生产。      
```
    public E take() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (count == 0)
                notEmpty.await();
            return dequeue();
        } finally {
            lock.unlock();
        }
    }

    private E dequeue() {
	//...
        notFull.signal();
        return x;
    }
```






