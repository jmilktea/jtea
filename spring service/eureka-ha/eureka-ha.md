## 基本概念
![image](https://github.com/jmilktea/jmilktea/blob/master/spring%20service/eureka-ha/eureka.png)

[服务注册]：服务启动时，会通过rest请求向配置的注册中心进行注册，告诉配置中心自己的名称和地址等信息，这些信息保存在eureka server的一个ConcurrentHashMap中。服务只会向第一个注册中心进行注册，如果失败，才继续向其它注册中心注册。

[服务同步]：注册中心会通过相互注册形成集群，并且相互同步服务信息。

[获取服务]：服务会通过rest请求向注册中心获取服务信息，并且缓存在本地，如果注册中心挂了，依然可以调用其它服务。默认是30s获取一次。

[服务调用]：使用本地缓存的服务列表信息进行服务调用，并且实现负载均衡

[服务续约]：默认30s会向注册中心举行续约，告诉注册中心自己还活着

[服务下线]：下线时可以通过rest请求告诉注册中心，注册中心会把这个消息通知其他注册中心和服务

[服务剔除]：eureka默认情况下会每60s检查一遍，把有90s没续约的服务剔除

## 存储机制
![image](https://github.com/jmilktea/jmilktea/blob/master/spring%20service/eureka-ha/eureka-store.png)  

[registry]：是一个嵌套的ConcurrentHashMap，第一层key是服务名称，value是一个Map。第二层key是实例id，value 是一个记录ip,端口等信息的对象。eureka server ui显示的就是从registry读取

[readWriteCacheMap]：是一个guava cache，存储了注册信息，默认情况下每60s就会清理90s没有来续约的client。readWriteCacheMap和registry是实时同步的

[readWriteCacheMap]：是一个只读的map，数据定时从readWriteCacheMap同步，默认是30s同步一次。client每30s就会向server获取最新信息，是从readOnlyCacheMap获取
