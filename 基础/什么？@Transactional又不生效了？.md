## 前言  
最近有同事反映使用spring声明式注解事务，也就是@Transactional无效。这个问题我们之前也分析过一个案例：[@Transactional不生效]()，但这次他出现的场景并不是文中说的同个类内调用事务方法，而是多线程场景。具体就是在@Transactional事务方法内，开启多个线程，并发处理数据，并且是希望如果某条数据有问题，可以整个回滚。如标题所示，这种情况下事务失效，这也是一个比较经典的问题，接下来我们分析一下。  

## 分析  
代码重现，下面insert 99,100两行没有任何疑问，肯定受事务管理，下面开启了10个线程，本意是直行到uid=5时，希望整个回滚，但事实是数据都落库了。  
```
	@Transactional(rollbackFor = Exception.class)
	public void testTransactional2() throws InterruptedException {
		accountMapper.insert(99);
		accountMapper.insert(100);

		for (int i = 0; i < 10; i++) {
			final int uid = i;
			Executors.newFixedThreadPool(10).submit(() -> {
				System.out.println(MessageFormat.format("thread:{0} run", Thread.currentThread().getId()));
				accountMapper.insert(uid);
				if (uid == 5) {
					throw new RuntimeException("throw exception");
				}
			});
		}
		Thread.sleep(10000);
	}
```
我们知道要使事务生效，最重要的一点就是确保直行的sql都在同一个链接，那么很明显上面违反了这个原则。接下来我们分析一下  
当执行@Transactional标记方法就会通过DataSourceTransactionManager的doBegin方法开启一个事务
```
DataSourceTransactionManager#doBegin
```
该方法内会通过TransactionSynchronizationManager事务同步管理器将链接绑定到线程上下文
```
// Bind the connection holder to the thread.
if (txObject.isNewConnectionHolder()) {
	TransactionSynchronizationManager.bindResource(obtainDataSource(), txObject.getConnectionHolder());
}
```
bindResource方法会将链接设置到TransactionSynchronizationManager的resources属性中，可以看到这是一个ThreadLocal对象  
```
	private static final ThreadLocal<Map<Object, Object>> resources =
			new NamedThreadLocal<>("Transactional resources");
```  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%9F%BA%E7%A1%80/images/transactional2-1.png)    
后续的操作会通过TransactionSynchronizationManager#getResource获取链接，很明显，如果线程上下文已经存在，那么就返回返回这个链接    
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%9F%BA%E7%A1%80/images/transactional2-2.png)  

很明显，多线程执行时，在不同的线程上下文，是没有共享同一个链接的，那么事务自然无效。  

## 扩展    
使用多线程的目的是为了加快速度，我们可以使用批量的方式来执行，例如将10000条数据插入，使用一条insert values sql明显比执行10000条insert语句要快很多。但是通常情况下，我们还是建议合理将语句拆成多个小事务，多次提交，例如每次处理100条数据比较合理，而不是一次处理上万条。一次处理太多数据，数据库锁的时间占用长，会阻塞其它操作，另外也会导致应用的内存飙升，而每次处理一小批，可以快速释放资源。  

多线程事务，本质上已经是一个分布式事务的问题了，而要解决分布式事务比较复杂，2pc，tcc，可靠消息...，很明显我们通常不愿意把复杂性提高。  

网上还有一种做法，我们参考一下代码
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%9F%BA%E7%A1%80/images/transactional2-3.png)

这种方式通过使用编程式事务手动提交或回滚事务，通过CountDownLatch让线程互相等待，一起提交或回滚。这类似于2阶段提交，没有根本上解决分布式事务问题。例如在每个线程commit时，部分commit成功了，此时服务挂了，那么没commit的就没了，而已经提交了不会回滚。  

本质上还是需要从业务角度去解决这个问题，尽量通过拆分大事务，避免一次处理太多数据，如果在业务允许范围内，每次处理的数据少了，那么自然可以不需要多线程了。  



