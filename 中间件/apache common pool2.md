## 简介
为了避免频繁创建和销毁资源对象，通常我们会使用池化的思想对其进行复用，例如线程，http请求，数据库链接，redis链接，复杂的大对象等，这些在创建的时候都是非常消耗资源的，如果每次用完就丢弃消耗，性能会严重下降。池化的思想就是通过一个对象池来合理管理这些对象，对象的创建，销毁，管理等操作都交给对象池，应用只需要向它发起请求即可，常见的对象池有线程池，http链接池，数据库链接池等。   

对象池如何实现管理池中的对象呢？它需要具备如下常见功能：  
1.往池中添加对象，提供使用  
2.从池中获取对象，使用后归还对象  
3.对象从池中移除，当超过一定时间没有使用时，对象可以被回收  
4.超时控制，可以设置超时时间，获取不到就报超时   
5.池中可以保持一定的对象数，这些对象不被销毁，以便快速使用   
...等等   

我们以熟悉的线程池为例，配置如下：  
```
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(10); //设置核心线程数，初始化时就会创建这个数量的线程
executor.setMaxPoolSize(10); //设置最大线程数  
executor.setQueueCapacity(100); //设置阻塞队列长度  
executor.setKeepAliveSeconds(300); //设置空闲时间  
executor.setAllowCoreThreadTimeOut(true); //core线程允许销毁  
executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); //阻塞策略  
executor.setThreadNamePrefix(namePrefix); //线程名称   
```  

每种池都有类似的配置来控制对象池的行为，如果我们要开发一个对象池来控制自己的对象就需要实现上面类似的功能，已经有现有的轮子供我们使用，不需要从0开始。  
apache common pool2 就是一个通用的对象池的实现，已经实现了常用功能，我们可以直接使用。  
pool2有3个核心对象，如下：  
**ObjectPool**：是对象池，它定义了上面我们所说的对象池所具备的行为  
**PoolObjectFactory**: 对象工厂，用于创建池中的对象，它定义了如何创建对象等    
**PoolObject**: 池中管理的对象，包装了我们的实际对象     

这个过程也很简单，如下图：  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/pool2-1.png)  

pool2已经内置了一些实现类，我们可以直接使用，如果需要自定义行为，只要实现核心接口即可。  
pool2也有许多实际应用，redis客户端jeids和lettuce都使用它来管理链接对象。以lettuce为例，要开启链接池需要导入pool2包  
```
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
    <version>2.10.0</version>
</dependency>
```
具体实现是在ConnectionPoolSupport类，源码如下：    
```
private static class RedisPooledObjectFactory<T extends StatefulConnection<?, ?>> extends BasePooledObjectFactory<T> {

        private final Supplier<T> connectionSupplier;

        RedisPooledObjectFactory(Supplier<T> connectionSupplier) {
            this.connectionSupplier = connectionSupplier;
        }

        @Override
        public T create() throws Exception {
            return connectionSupplier.get();
        }

        @Override
        public void destroyObject(PooledObject<T> p) throws Exception {
            p.getObject().close();
        }

        @Override
        public PooledObject<T> wrap(T obj) {
            return new DefaultPooledObject<>(obj);
        }

        @Override
        public boolean validateObject(PooledObject<T> p) {
            return p.getObject().isOpen();
        }
    }

    private static class ObjectPoolWrapper<T> implements Origin<T> {

        private static final CompletableFuture<Void> COMPLETED = CompletableFuture.completedFuture(null);

        private final ObjectPool<T> pool;

        ObjectPoolWrapper(ObjectPool<T> pool) {
            this.pool = pool;
        }

        @Override
        public void returnObject(T o) throws Exception {
            pool.returnObject(o);
        }

        @Override
        public CompletableFuture<Void> returnObjectAsync(T o) throws Exception {
            pool.returnObject(o);
            return COMPLETED;
        }
    }
```

## 示例
```
    @Data
	@AllArgsConstructor
	class Connection {
		private String id;
	}

	@Data
	@AllArgsConstructor
	class ConnectionFactory extends BasePooledObjectFactory<Connection> {

		@Override
		public Connection create() throws Exception {
			return new Connection(UUID.randomUUID().toString());
		}

		@Override
		public PooledObject<Connection> wrap(Connection obj) {
			return new DefaultPooledObject<>(obj);
		}
	}

	@Test
	public void test() throws InterruptedException {
        //对象池配置
		GenericObjectPoolConfig config = new GenericObjectPoolConfig();
		config.setMinIdle(1); //最小空闲对象数
		config.setMaxIdle(1); //最大空闲对象数
		config.setMaxTotal(3); //连接池对象数
		config.setMaxWaitMillis(3000); //获取链接超时时间
		config.setTestWhileIdle(true);
		config.setMinEvictableIdleTime(Duration.ofSeconds(8));
		config.setSoftMinEvictableIdleTime(Duration.ofSeconds(8));
		config.setTimeBetweenEvictionRuns(Duration.ofMillis(200)); //设置检查时间，必须设置这个参数，否则minEvictableIdleTime无效
		config.setEvictionPolicy(new DefaultEvictionPolicy());

		ConnectionFactory connectionFactory = new ConnectionFactory();
		ObjectPool objectPool = new GenericObjectPool(connectionFactory, config);
		for (int i = 0; i < 10; i++) {
			new Thread(() -> {
				try {
					//从池中获取对象
					Connection connection = (Connection) objectPool.borrowObject();
					System.out.println(connection.getId());
					Thread.sleep(5000);
					//归还对象
					objectPool.returnObject(connection);
				} catch (Exception e) {
                    //超时获取不到对象
					System.out.println(e.getMessage());
				}
			}).start();
		}
		Thread.sleep(15000);
		//池中空闲对象数会被销毁
		System.out.println("current connection count:" + objectPool.getNumIdle());
		Thread.sleep(Integer.MAX_VALUE);
	}
```
输出：  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/pool2-2.png)  
