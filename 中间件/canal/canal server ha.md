## 原理  
前面的quick start快速开始了一个canal server，让我们对canal有一个整体性的了解，但quick start是一个单机版，没有HA，只适合本地做一些测试。  
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
为了简单实现，我们基于前面的quick start，同一台机器上部署两个canal server。
- 修改 canal.properties 配置  
```
# 由于在同一台机器上，所以这些端口要不一样
canal.port=21111
canal.metrics.pull.port=21112
canal.admin.port=21110 

canal.zkServers=zkip:port #zookeeper的ip:端口
canal.instance.global.spring.xml = classpath:spring/default-instance.xml #使用default模式，之前单机版的是file
canal.destinations = example #这里还是用默认的example，需要注意，两个canal server的目录名称需要保持一致
```
- 修改 example/instance.properties
```
canal.instance.mysql.slaveId = 11 ##另外一个改成21，保证slaveId不重复即可，不要和mysql master配置的冲突  
```
- 分别启动两个canal server
- zk 查看运行节点
```
 get /otter/canal/destinations/example/running   
 {"active":true,"address":"172.20.0.1:11111"}
```
可以看到11111的节点处于runing状态，21111的处于standby  
- zk 查看消费位点  
```
get /otter/canal/destinations/example/1001/cursor  
{"@type":"com.alibaba.otter.canal.protocol.position.LogPosition","identity":{"slaveId":-1,"sourceAddress":{"address":"localhost","port":3306}},"postion":{"gtid":"","included":false,"journalName":"mysql-bin.000004","position":13885,"serverId":1,"timestamp":1591600659000}}
```
可以看到消费到mysql-bin.000004这个文件的13885位置  
- canal server切换  
将canal server 11111 下线，再次查看  
```
 get /otter/canal/destinations/example/running   
 {"active":true,"address":"172.20.0.1:21111"}
```
可以看到，备用的canal server启动了，并且可以正常消费binlog  

## 问题
- 重复消费  
测试过程中发现，当发生canal server切换时，最后面的binlog有些会重复消费，目前issue上也有不少人反应这个问题。虽然会重复发送，但是也会按照顺序发送到最新位置。


