## 简介
单个节点的nacos server非常简单，一个命令即可启动，但单节点不具备高可用的，作为注册中心+配置中心，必须实现高可用，解决单点问题。本次将在同个虚拟机上搭建3个节点的nacos-server集群，存储方面使用mysql，方便观察。  
熟悉eureka的朋友都知道，eureka server通过相互注册实现集群，eureka server节点之前是平等的，并且会通过复制实现信息同步。nacos server也会通过配置发现其它集群节点，不同的是节点间会选举一个leader（蛇无头不行，群龙不能无首），而其它节点都作为folower，当集群认为leader出现故障，会触发新一轮选举，选举出新leader。这个过程是通过raft算法实现，raft是一种易于理解的分布式一致性算法在分布式系统中，节点间需要达成共识，才能对外提供可靠，一致的服务。关于raft算法这个动态图可以很形象的介绍：[raft动态图](http://thesecretlivesofdata.com/raft/)

## 步骤
- 创建nacos数据库   
[建表脚本](https://github.com/alibaba/nacos/blob/master/distribution/conf/nacos-mysql.sql)

- 创建nacos节点目录  
下载nacos server，将其解决到node1,node2,node3三个节点目录  
```
tar -zxvf nacos-server-1.1.3.tar.gz -C /usr/nacos/test/node1 -C /usr/nacos/test/node2 -C /usr/nacos/test/node3
```

- application.properties配置  
找到node1/nacos/conf/application.properties配置文件，加入如下配置：
```
db.num=1
db.url.0=jdbc:mysql://dbip:3306/nacos?characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true
db.url.1=jdbc:mysql://dbip:3306/nacos?characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true
db.user=root
db.password=123456
```
并且将端口修改为8850  

- cluster.conf配置  
```
node1ip:8850
node2ip:8851
node3ip:8852
```
配置3个节点的ip和端口

- 启动  
同理配置node2和node3，修改端口为8851和8852，使用sh startup.sh 分别启动3个节点。效果如图：


使用如下命令，可以看到注册中心多了一个服务
```
curl -X PUT 'http://127.0.0.1:8850/nacos/v1/ns/instance?serviceName=nacos.naming.serviceName&ip=20.18.7.10&port=8080'
```

## 问题   
nacos的日志分得很详细，都在logs目录下

- 启动失败  
可以到nacos/logs下查看start.out日志，例如可能是虚拟机内存不足，集群模式下nacos启动需要2g内存，可以通过修改startup.sh降低初始内存  

- 节点都启动成功，但界面集群显示为空，或者curl注册服务时出现：nacos server is STARTING now, please try again later    
没有集群创建失败，可以查看/logs/naming-raft.log，出现
```
WARN [IS LEADER] no leader is available now!
```
警告表示leader选举失败，检查cluster.conf 配置，改成具体ip:端口，不要使用localhost或者127.0.0.1  

## 思考     
客户端如何访问集群？  
方式1：通过配置集群节点ip。缺点是要写死ip和端口，当需要新增或修改节点的时候，客户端也需要修改，不利于扩展。    
方式2：通过域名转发，优点是对客户端透明，集群可以动态伸缩，如图：

leader节点挂了怎么办？  
按照raft流程，leader挂了会重新选举。我们kill掉leader节点，可以看到集群重新选举了一个新leader，再次把该节点kill掉，可以看到最后一个节点变成Candidate，并且不会再成为leader。重新把kill的节点启动，选举重新进行（term递增），选出新的leader。