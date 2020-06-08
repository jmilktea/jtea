## 原理  
前面的[quick start]快速开始了一个canal server，让我们对canal有一个整体性的了解，但quick start是一个单机版，没有HA，只适合本地做一些测试。  
canal的ha分为两部分，canal server和canal client分别有对应的ha实现：  
- canal server: 为了减少对mysql dump的请求，不同server上的instance要求同一时间只能有一个处于running，其他的处于standby状态.
- canal client: 为了保证有序性，一份instance同一时间只能由一个canal client进行get/ack/rollback操作，否则客户端接收无法保证有序。  
这里我们主要关注canal server的HA。  

canal server 的ha依赖zookeeper，可以有多个canal server注册到zk上，但只有一个处于running状态，但这个节点不可用时，备用的canal server就会接管running。整个流程如下：  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/canal/images/canal-server-ha.png)    
大致步骤：
- canal server要启动某个canal instance时都先向zookeeper进行一次尝试启动判断 (实现：创建EPHEMERAL节点，谁创建成功就允许谁启动)
- 创建zookeeper节点成功后，对应的canal server就启动对应的canal instance，没有创建成功的canal instance就会处于standby状态
- 一旦zookeeper发现canal server A创建的节点消失后，立即通知其他的canal server再次进行步骤1的操作，重新选出一个canal server启动instance.
- canal client每次进行connect时，会首先向zookeeper询问当前是谁启动了canal instance，然后和其建立链接，一旦链接不可用，会重新尝试connect.

## 实现
这里