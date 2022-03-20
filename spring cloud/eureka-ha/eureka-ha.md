## 基本概念
![image](https://github.com/jmilktea/jmilktea/blob/master/spring%20cloud/eureka-ha/eureka.png)

[服务注册]：服务启动时，会通过rest请求向配置的注册中心进行注册，告诉配置中心自己的名称和地址等信息，这些信息保存在eureka server的一个ConcurrentHashMap中。服务只会向第一个注册中心进行注册，如果失败，才继续向其它注册中心注册。

[服务同步]：注册中心会通过相互注册形成集群，并且相互同步服务信息。

[获取服务]：服务会通过rest请求向注册中心获取服务信息，并且缓存在本地，如果注册中心挂了，依然可以调用其它服务。默认是30s获取一次。

[服务调用]：使用本地缓存的服务列表信息进行服务调用，并且实现负载均衡

[服务续约]：默认30s会向注册中心举行续约，告诉注册中心自己还活着

[服务下线]：下线时可以通过rest请求告诉注册中心，注册中心会把这个消息通知其他注册中心和服务

[服务剔除]：eureka默认情况下会每60s检查一遍，把有90s没续约的服务剔除

## 存储机制
![image](https://github.com/jmilktea/jmilktea/blob/master/spring%20cloud/eureka-ha/eureka-store.png)  

[registry]：是一个嵌套的ConcurrentHashMap，第一层key是服务名称，value是一个Map。第二层key是实例id，value 是一个记录ip,端口等信息的对象。eureka server ui显示的就是从registry读取。

[readWriteCacheMap]：是一个guava loading cache，默认过期时间是180s，readWriteCacheMap和registry是实时同步的。

[readOnlyCacheMap]：是一个只读的ConcurrentHashMap，数据定时从readWriteCacheMap同步，默认是30s会同步readWriteCacheMap的数据。

client会定时从eureka server获取最新的实例数据，先从readOnlyCacheMap读取，如果读取不到，就从readWriteCacheMap读取，如果读取不到就从registry读取，并将读取到的数据放到readWriteCacheMap和readOnlyCacheMap。   

**为什么要设计三级缓存**   
首先一级缓存使用ConcurrentHashMap是毫无疑问的，简单，高效，线程安全。   

那为什么需要二级缓存，直接用registry这个ConcurrentHashMap不行吗？这是eureka为了实现“读写分离”，ConcurrentHashMap内部本身读取某个key是不需要加锁的，但是整个ConcurrentHashMap可能处于不断写入和修改的过程，为了保证读写一致性，能读到正确的数据，就需要加锁，eureka使用的是ReentrantReadWriteLock读写锁。    
例如节点注册(com.netflix.eureka.registry.AbstractInstanceRegistry#register)，节点状态更新(com.netflix.eureka.registry.AbstractInstanceRegistry#statusUpdate)，节点下线(com.netflix.eureka.registry.AbstractInstanceRegistry#cancel)，eureka都会加上读锁(eentrantReadWriteLock.ReadLock)，在写锁没有被持有的情况下才获获取锁成功，由于ConcurrentHashMap是支持并发写的，所以这里多个写操作同时获取到锁进行写操作是没有问题的。     
反过来获取节点信息(com.netflix.eureka.registry.AbstractInstanceRegistry#getApplicationDeltasFromMultipleRegions)会加写锁，在读/写锁都没有被持有的情况下才会获取成功。（这里看起来有点绕，读写锁的本意就是不允许写线程和读线程，或者写线程和写线程同时访问）。具体代码都在AbstractInstanceRegistry这个类中。    
因此为了提高并发读写的效率，设计了二级缓存，二级缓存是个guava Loading CacheMap，key默认180s就会过期，所以它的设计是用来缓存热点数据的。    

那为什么要设计三级缓存呢，而且默认还是30s同步一次，导致读不到最新数据。首先确实是可以不要三级缓存的，可以通过参数eureka.server.use-read-only-response-cache:false 关闭使用三级缓存，默认是开启的。使用三级缓存的主要意义还是出于读取性能考虑，没有读写并发从这里读的效率是最快的，否则就需要一级一级的往上读，在二级缓存读不到就需要到一级缓存读，相当于把读的压力又回到了一级缓存。同时我认为三级缓存“可能”还有一个意义在于，当某个服务由于网络问题暂时没有按照约定进行续约，eureka会从一二级缓存拿掉，此时有三级缓存其它服务就会认为它依然活着，实际它也确实活着，就不会出现误判，当然这样会导致它真的下线了，而其它服务不能马上感知到。    







