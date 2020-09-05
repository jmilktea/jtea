## 前言  
redis的常见架构模式有：单机、主从、哨兵和集群模式几种，对于一些简单的应用或者开发测试环境可以使用单机模式，而要求较高的一般会使用集群模式，本篇就来实现这两种模式的搭建。

## 单机  
本次搭建是基于linux环境，redis官方没有提供windows版本的redis，需要在windows安装可以参考这里：[redis安装教程](https://www.runoob.com/redis/redis-install.html)。单机的安装步骤如下：
1. 安装gcc
```
gcc -v 
yum install gcc
```
2. 下载、解压安装包
```
wget http://download.redis.io/releases/redis-5.0.8.tar.gz
tar -zxvf redis-5.0.8.tar.gz
```
3. 编译
```
cd redis-5.0.8
make MALLOC=libc
```
4. 安装到指定目录
```
cd src && make install PREFIX=/usr/redis
```

完成之后，在/usr/redis就会出现安装后的bin目录，我们可以把redis-5.0.8/redis.conf配置文件也拷到/usr/redis下，接着就可以启动redis服务了  
```
./bin/redis-server redis.conf 
./bin/redis-cli shutdown #停止服务
```

如果需要修改一些配置，[可以参考这里](https://github.com/jmilktea/jmilktea/blob/master/redis/redis%E5%B8%B8%E7%94%A8%E9%85%8D%E7%BD%AE%E5%8F%82%E6%95%B0.md)

安装过程可能遇到的问题：
- cc: 错误：../deps/hiredis/libhiredis.a：没有那个文件或目录  
解决：到 redis-5.0.8/deps 执行如下命令
```
make lua hiredis linenoise
```

## 集群  
集群的搭建是在单机的基础上完成的，为了保证高可用性，redis集群要求至少需要6个节点，其中3个主节点3个从节点，从节点会同步主节点的数据。节点之间是平等的，没有master-slave之分，他们相互通信。当主节点挂了，从节点会顶上代替成为主节点，当节点恢复，会变成从节点。redis集群是以槽位单位的，总共有16383个槽位，会分配到主节点上，当一个key需要写到redis集群时，会先通过算法计算这个key落在哪个槽上，再写到这个槽所在的redis。  
redis集群架构图如下：   
![image](https://github.com/jmilktea/jmilktea/blob/master/redis/images/redis-cluster.png)  
redis集群的搭建是在单机的基础上完成的，本次搭建是在同一个机器上搭建6个节点，对于多个机器也是一样道理，可以相互访问就可以。  

1. 首先创建redis01 redis02 redis03 redis04 redis05 redis06 目录，分别对应6个节点，接着把单机安装的bin目录redis.conf文件分别拷贝到这些目录下  
2. 编辑各个redis.conf配置文件，主要修改如下配置：
```
port 7001 #7002 7003 7004 7005 7006
bind 127.0.0.1 #默认，如果是多台机器需要修改为可访问的ip
daemonize yes #开启后可以后台运行
cluster-enabled yes #开启集群模式
```
3. 各目录下启动各个服务，启动成功可以看到6个redis进程  
```
./bin/redis-server redis.conf
```
4. 创建集群。随便进入一个节点，执行
```
./redis-cli --cluster create 127.0.0.1:7001 127.0.0.1:7002 127.0.0.1:7003 127.0.0.1:7004 127.0.0.1:7005 127.0.0.1:7006 --cluster-replicas 1
```
其中 --cluster-replicas 1 表示每个主节点有一个从节点。如果是在多台机器上部署，127.0.0.1需要改成对应ip。  
5. 查看集群  
创建成功后，随便找一个节点，通过redis-cli进入redis 
```
redis-cli -c -h 127.0.0.1 -p 7001
```  
查看集群节点信息，可以看到三主三从，以及主节点槽位的分配范围。
```
cluster nodes
```  
![image](https://github.com/jmilktea/jmilktea/blob/master/redis/images/redis-cluster-nodes.png)  
6. 操作  
进入节点后就可以使用redis命令，从上面可以知道每个节点都有一个槽位范围，如果操作的刚好落在当前节点就会正常运行，否则会访问一个MOVED错误，表示需要重定向到另一个节点，这个时候redis客户端就应该重新连接到另一个节点进行操作。关于MOVED是redis协议的一部分，[可以参考这里](https://github.com/jmilktea/jmilktea/blob/master/redis/resp%E5%8D%8F%E8%AE%AE.md)   
![image](https://github.com/jmilktea/jmilktea/blob/master/redis/images/redis-cluster-getset.png)  
