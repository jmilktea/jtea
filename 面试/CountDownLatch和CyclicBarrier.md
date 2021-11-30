- 开启三个线程，如何顺序输出"A","B","C"字符  
- 开启三个线程，主线程如何在三个线程都达到某种状态时再执行某个动作   
- 开启三个线程，如何在三个线程都达到某种状态时再执行某个动作   
- CountDownLatch/CyclicBarrier的区别是什么  


上面的问题是在面试时常问的多线程问题，问题一作为热身引入。    
按顺序输出A,B,C，的实现方式比较多，多线程之间是并发、执行顺序不确定的，这里的要求需要顺序输出。  
我们可以把线程添加到一个队列，考虑到并发问题，需要使用线程安全的队列。可以用SynchronousQueue，然后用一个消费者去消费它，按顺序打印字符，SynchronousQueue添加元素后如果没有消费者消费，后面的生产者会被阻塞。也可以用concurrent包下的ConcurrentLinkedDeque，也是线程安全的，将线程加入队列后再取出执行。这种方式也许不是面试官想要的场景，但是它可以展示我们的知识面，是个加分项。   
一般问到这个问题，是在考线程的join方法，join方法可以让线程等待，直到线程完成。我们看下demo   
```
	@Test
	public void test() throws InterruptedException {
		Thread threadA = new Thread(() -> {
			System.out.println("A start");
			System.out.println("A");
		});
		Thread threadB = new Thread(() -> {
			try {
				System.out.println("B start");
				threadA.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println("B");
		});
		Thread threadC = new Thread(() -> {
			try {
				System.out.println("C start");
				threadB.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println("C");
		});
		threadA.start();
		threadB.start();
		threadC.start();
		Thread.sleep(10000);
	}
```
运行上面的例子，start的输出顺序是不确定的，但是由于thread B,C 都调用了join方法，所以会等待对应的线程执行完成，所以A,B,C输出顺序是确定的。   
**join原理**    
我们看下join方法的源码    
```
public final synchronized void join(long millis)
    throws InterruptedException {
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
无参的join方法会调用上面的方法，参数传0表示无限等到，millis可以指定等待的时间，超时则不等了，对应代码中的break分支。   
代码中通过一个while循环不断判断线程是否还存活，如果是则调用wait方法，wait-notify/notifyAll机制用于线程的等待和唤醒，所以join的内部是使用了这个机制，这里我们看到了wait方法的调用，那么notify是在哪里调用呢？这个需要跟踪到jvm底层源码，在线程处理完成后jvm底层会调用notifyAll通知可能在wait的线程。    

上面是按线程顺序输出，如果我们想多个线程执行后再执行某个动作呢？实际业务场景我们可能会在主线程开多个线程或者用线程池去执行任务，但是主线程需要等子线程都执行完成后再继续执行。   
这种场景我们可以使用CountDownLatch。Latch是锁的意思，CountDownLatch在concurrent包下，是一个线程安全的计数器，我们可以设置一个初始的计数，每完成一步计数-1，当计数为0时，await方法会被通过。   
demo如下：  
```
            CountDownLatch countDownLatch = new CountDownLatch(list.size());
            list.stream().forEach(item -> {
                        threadPool.submit(() -> {
                            try {
                               insert(item);  
                            } finally {
                                countDownLatch.countDown();
                            }
                        });
                    }
            );
            countDownLatch.await();
            log.info("done");
```
list每个项都会提交给线程池去并发执行，当list遍历完成线程池里的任务可能还没全部完成，CountDownLatch计数还不为0，await方法就会阻塞。    
我们说CountDownLatch是线程安全的，那么它是怎么做到线程安全的呢？   
countDown方法如下   
```
  public void countDown() {
        sync.releaseShared(1);
    }
    
    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            doReleaseShared();
            return true;
        }
        return false;
    }
```  
sync是一个内部类，实现了AQS   
```
    private static final class Sync extends AbstractQueuedSynchronizer {
    
        Sync(int count) {
            setState(count);
        }

        int getCount() {
            return getState();
        }

        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }

        protected boolean tryReleaseShared(int releases) {            
            for (;;) {
                int c = getState();
                if (c == 0)
                    return false;
                int nextc = c-1;
                if (compareAndSetState(c, nextc))
                    return nextc == 0;
            }
        }
    }
```   
在并发的情况下，sync使用CAS对计数进行-1，上面的for是个死循环，在并发大的时候CAS失败次数可能会比较高，消耗较多的cpu资源。关于CAS可以参考[这里](https://github.com/jmilktea/jmilktea/blob/master/%E5%9F%BA%E7%A1%80/cas.md)   
当调用await时，会调用tryAcquireShared判断计数是否为0，如果否就会使用LockSupport.part将当前线程挂起，那么挂起后什么时候恢复执行呢？看到上面的releaseShared方法，tryReleaseShared方法在CAS成功后会判断计数是否为0，是则返回true，接着会执行doReleaseShared方法，内部会通过LockSupport.unpart将挂起的线程恢复。    

上面的场景主线程等待多个线程执行完后再继续执行，还有一种场景是多个线程等待彼此都执行到某个状态后多个线程再一起继续执行。   
这种场景可以使用**CyclicBarrier**，cyclic是循环的意思，barrier是屏障的意思，通过设置一个屏障阻塞线程，当达到某种状态时，屏障解除，线程继续执行。   
看下demo   
```
	@Test
	public void test2() throws InterruptedException {
		CyclicBarrier cyclicBarrier = new CyclicBarrier(2, () -> {
			System.out.println("done");
		});
		new Thread(() -> {
			try {				
				System.out.println("1");
				cyclicBarrier.await();
                System.out.println("11");
			} catch (BrokenBarrierException e) {
				e.printStackTrace();
			}
		}).start();
		new Thread(() -> {
			try {				
				System.out.println("2");
				cyclicBarrier.await();
                System.out.println("22");
			} catch (BrokenBarrierException e) {
				e.printStackTrace();
			}
		}).start();		
	}
```
上面的代码设置了屏障是2，两个线程任何一个先执行println后就会达到屏障被阻塞，只有等另一个线程也达到，屏障才会解除，所有线程继续执行后面的代码。    
这个看起来和CountDownLatch很像，我们使用CountDownLatch也可以完成上面的功能，例如两个线程都先执行countDown,再执行await。但是可以看出CountDownLatch实现起来麻烦一些，需要调用两个方法，并且CyclicBarrier还提供了一些其它功能，如可以获取正在waitting的个数，CountDownLatch的功能很简单。另外最主要的区别在于cyclic循环，这表示CyclicBarrier是可以循环使用的，使用完后可以调用reset重置屏障，这样就可以复用了。而CountDownLatch是一次性的，也就是说如果我们想继续使用，就不得不重新new一个对象，重新计数。当然，还是需要根据我们上面说的场景进行选择。    
