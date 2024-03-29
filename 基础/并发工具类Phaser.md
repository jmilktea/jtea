# 前言
在面试这一篇我们介绍过[CountDownLatch和CyclicBarrier](https://github.com/jmilktea/jtea/blob/master/%E9%9D%A2%E8%AF%95/CountDownLatch%E5%92%8CCyclicBarrier.md)，它们都是jdk1.5提供的多线程并发控制类，内部都是用AQS这个同步框架实现。    
在我们的实际项目中，有很多场景是需要从数据库查询一批数据，多线池执行某些操作，并且要统计结果，我们对这个过程做了一些封装，由于要统计结果，所以需要等所有任务都处理完成，我们用到了CountDownLatch实现同步。伪代码如下：  
```
        ExecuteInstance ei = ExecuteInstance.build(myExecutor); //线程池
		
        //循环
        LoopShutdown.build("myTask").loop(() -> {

            //不断从数据获取数据
            List<Task> list = getFromDb();
            
            //设置countdownlatch
  	    ei.setCountDownSize(list.size());

	    list.forEach(item -> ei.execute(() -> {
		//提交到线程池执行，并且统计
	    }));
            
            //等待这一批做完
	    ei.await();
		
	});

        //内部使用了CountDownLatch await()
	return ei.awaitResult();
```
代码很简单，容易理解。不过后来有同学提到每次都要setCountDownSize() + await() 这套组合太麻烦，能不能省略这两步呢。另外也不够灵活，有些场景不能提前知道要处理的数据总数，例如从迭代器遍历数据，Iterator接口并没有size方法可以获取到总数。    

那怎么实现这个功能呢？就是本篇要介绍的Phaser。    

# Phaser原理        
Phaser类是jdk7提供的，可重用的，同步的，在功能上和CountDownLatch，CyclicBarrier类似，但更加灵活的类。    
"phaser" google翻译一下是："移相器"的意思，完全不知道是什么~，不过"phase"是阶段的意思，还是能从名字了解到一些信息。   

Phaser运行机制：   
![image1](https://github.com/jmilktea/jtea/blob/master/%E5%9F%BA%E7%A1%80/images/phaser-1.png.png)       

- Registration(注册)    
跟其他barrier不同，在phaser上注册的parties会随着时间的变化而变化。任务可以随时注册(使用方法register,bulkRegister注册，或者由构造器确定初始parties)，并且在任何抵达点可以随意地撤销注册(方法arriveAndDeregister)。就像大多数基本的同步结构一样，注册和撤销只影响内部计数；不会创建更深的内部记录，所以任务不能查询他们是否已经注册。(不过，可以通过继承来实现类似的记录)     
可以动态的注册是它的特点之一，我们知道CountDownLatch之类的在开始就需要指定一个计数，并且不能更改，而Phaser可以开始指定，也可以运行时更改。   

- Synchronization(同步机制)    
和CyclicBarrier一样，Phaser也可以重复await。方法arriveAndAwaitAdvance的效果类似CyclicBarrier.await。phaser的每一代都有一个相关的phase number，初始值为0，当所有注册的任务都到达phaser时phase+1，到达最大值(Integer.MAX_VALUE)之后清零。使用phase number可以独立控制到达phaser和等待其他线程的动作，通过下面两种类型的方法:

	**Arrival(到达机制)** arrive和arriveAndDeregister方法记录到达状态。这些方法不会阻塞，但是会返回一个相关的arrival phase number；也就是说，phase number用来确定到达状态。当所有任务都到达给定phase时，可以执行一个可选的函数，这个函数通过重写onAdvance方法实现，通常可以用来控制终止状态。重写此方法类似于为CyclicBarrier提供一个barrierAction，但比它更灵活。

	**Waiting(等待机制)** awaitAdvance方法需要一个表示arrival phase number的参数，并且在phaser前进到与给定phase不同的phase时返回。和CyclicBarrier不同，即使等待线程已经被中断，awaitAdvance方法也会一直等待。中断状态和超时时间同样可用，但是当任务等待中断或超时后未改变phaser的状态时会遭遇异常。如果有必要，在方法forceTermination之后可以执行这些异常的相关的handler进行恢复操作，Phaser也可能被ForkJoinPool中的任务使用，这样在其他任务阻塞等待一个phase时可以保证足够的并行度来执行任务。   

- Termination(终止机制)    
可以用isTerminated方法检查phaser的终止状态。在终止时，所有同步方法立刻返回一个负值。在终止时尝试注册也没有效果。当调用onAdvance返回true时Termination被触发。当deregistration操作使已注册的parties变为0时，onAdvance的默认实现就会返回true。也可以重写onAdvance方法来定义终止动作。forceTermination方法也可以释放等待线程并且允许它们终止。     

- Tiering(分层结构)      
Phaser支持分层结构(树状构造)来减少竞争。注册了大量parties的Phaser可能会因为同步竞争消耗很高的成本， 因此可以设置一些子Phaser来共享一个通用的parent。这样的话即使每个操作消耗了更多的开销，但是会提高整体吞吐量。在一个分层结构的phaser里，子节点phaser的注册和取消注册都通过父节点管理。子节点phaser通过构造或方法register、bulkRegister进行首次注册时，在其父节点上注册。子节点phaser通过调用arriveAndDeregister进行最后一次取消注册时，也在其父节点上取消注册。    
这也是它的主要亮点之一，这一点很像ConcurrentHashMap(对HashTable)和LongAdder(对AtomicLong)，通过分散热点来降低资源竞争，提升并发效率。   
![image](https://github.com/jmilktea/jtea/blob/master/%E5%9F%BA%E7%A1%80/images/phaser-2.png)    

- Monitoring(状态监控)   
由于同步方法可能只被已注册的parties调用，所以phaser的当前状态也可能被任何调用者监控。在任何时候，可以通过getRegisteredParties获取parties数，其中getArrivedParties方法返回已经到达当前phase的parties数。当剩余的parties(通过方法getUnarrivedParties获取)到达时，phase进入下一代。这些方法返回的值可能只表示短暂的状态，所以一般来说在同步结构里并没有啥卵用。     

CountDownLatch和CyclicBarrier都非常简单，从Phaser提供的api数量就可以看出为什么说它更加灵活，show me the code，接下来我们通过几个例子感受一下。   
![image](https://github.com/jmilktea/jtea/blob/master/%E5%9F%BA%E7%A1%80/images/phaser-3.png)         

# Phaser例子     
例子1：子线程会等全部子线程达到后才开始执行，实现类似CyclicBarrier的效果。    
```
	@Test
	public void test1() throws InterruptedException {
		List<Runnable> list = Lists.newArrayList();
		for (int i = 0; i < 10; i++) {
			final int j = i;
			list.add(() -> System.out.println(j));
		}

		final Phaser phaser = new Phaser(); // "1" to register self
		// create and start threads
		int i = 0;
		for (final Runnable task : list) {
			i++;
			final int j = i;
			phaser.register();
			new Thread(() -> {
				try {
					Thread.sleep(j * 1000);
				} catch (InterruptedException e) {
				}
				//全部子线程到达后才开始执行
				phaser.arriveAndAwaitAdvance(); // await all creation
				task.run();
			}).start();
		}
		Thread.sleep(15000);
	}
```

例子2：task会循环做3次，通过重写onAdvance可以控制phaser结束的条件。    
```
    	@Test
	public void test2() throws InterruptedException {
		//重复做3次
		int iterations = 3;
		List<Runnable> list = Lists.newArrayList();
		for (int i = 0; i < 2; i++) {
			final int j = i;
			list.add(() -> System.out.println(j));
		}

		final Phaser phaser = new Phaser() {			
			//每做一次，phase+1，该方法返回true，就会结束
			protected boolean onAdvance(int phase, int registeredParties) {
				return phase > iterations || registeredParties == 0;
			}
		};
		phaser.register();
		for (final Runnable task : list) {
			phaser.register();
			new Thread(() -> {
				do {
					task.run();
					phaser.arriveAndAwaitAdvance();
				} while (!phaser.isTerminated());
			}).start();
		}
		phaser.arriveAndDeregister(); // deregister self, don't wait  
		Thread.sleep(5000);
	}
```

例子3：创建多个phaser，并关联到父phaser上，就是上面提到的分层结构。    
```
    	@Test
	public void test3() {
		Phaser parent = new Phaser(1);
		Phaser phaser1 = new Phaser(parent);
		Phaser phaser2 = new Phaser(parent);

		for (int i = 0; i < 20; i++) {
			final int j = i;
			if (i < 10) {
				phaser1.register();
				new Thread(() -> {
					try {
						Thread.sleep(1000);
						phaser1.arriveAndAwaitAdvance(); // await all creation
						System.out.println(j);
					} catch (InterruptedException e) {
					}
				}).start();
			} else if (i < 20) {
				phaser2.register();
				new Thread(() -> {
					try {
						Thread.sleep(10000);
						phaser2.arriveAndAwaitAdvance(); // await all creation
						System.out.println(j);
					} catch (InterruptedException e) {
					}
				}).start();
			}
		}
		parent.arriveAndAwaitAdvance();
		System.out.println("done");
	}
```

例子4：使用Phaser改写我们的代码，如下：
```
    	//维护一个Phaser    
	public static ExecuteInstance buildWithPhaser(Executor executor) {
		ExecuteInstance ei = new ExecuteInstance();
        	ei.executor = executor;
		ei.phaser = new Phaser(1);        
		return ei;
	}

    	//提交线程池前注册一下
    	public void executeRR(Callable<ReturnResult> task, Consumer<Exception> exceptionHandler, int batch) {
		phaser.register();
		executor.execute(() -> executeStatistics(task, exceptionHandler, batch));		
	}

    	//执行后deregister一下
    	private void executeStatistics(Callable<ReturnResult> task, Consumer<Exception> exceptionHandler, int batch) {
		ReturnResult result = ReturnResult.NONE;
		try {
        	    	//任务处理
			result = task.call();
		} catch (Exception e) {
			if (statistics) {
				counter.incrException(batch);
			}
			if (exceptionHandler != null) {
				//自定义异常处理
				try {
					exceptionHandler.accept(e);
				} catch (Exception he) {
				}
			}
		} finally {
			phaser.arriveAndDeregister(); //deregister   
			if (statistics) {
				if (ReturnResult.SUCCESS.equals(result)) {
					counter.incrSuccess(batch);
				} else if (ReturnResult.FAIL.equals(result)) {
					counter.incrFail(batch);
				} else if (ReturnResult.FILTER.equals(result)) {
					counter.incrFilter(batch);
				}
			}
		}
	}

    	//等待结果
    	public ExecuteResult awaitResult() {
		phaser.arriveAndAwaitAdvance();
		return getExecuteResult();
    	}
```
使用就非常简单了
```
	ExecuteInstance ei = ExecuteInstance.buildWithPhaser(myExecutor); //线程池
		
    	//循环
     	LoopShutdown.build("myTask").loop(() -> {

        	//不断从数据获取数据
        	List<Task> list = getFromDb();            

		list.forEach(item -> ei.execute(() -> {
			//提交到线程池执行，并且统计
		}));        	
	});

	return ei.awaitResult();
```

# 总结    
Phaser是jkd7后提供的同步工具类，它底层并没有使用AQS同步工具。相比CountDownLatch等它提供了更丰富的功能，但也意味着它更复杂，需要更多的资源，一些简单的场景CountDownLatch等工具类能满足的就使用它们即可，考虑性能，还有灵活性时才考虑使用Phaser，如笔者的场景使用Phaser就更加适合。    
