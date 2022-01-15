本篇我们来安装es，为后面的学习做准备。除了安装es，还会在单机上搭建一个es集群，初步了解集群的知识，以及安装kibana可视化工具，集群监控工具cerebro。    
这里我们使用的是elasticsearch 7.10.0版本，kibana的版本要和es保持一致。   
es的相关概念可以先看[这里]()    

## 安装es   
到es官网，[下载指定版本](https://www.elastic.co/cn/downloads/past-releases#elasticsearch)    
es是跨平台的，一般我们的服务器都是linux，这里我们选择linux系统，下载   
![image](1)    

下载解压后目录如下   
![image](2)   
说明：  
bin: 包含es的可执行脚本，例如使用./elasticsearch 可以启动es进程   
config: 配置文件，elasticsearch.yml可以配置es进程的相关参数，还有其它配置文件，例如jvm,log4j,用户角色的   
data: 数据文件，索引的数据保存在这个目录下    
jdk: 从es7开始，会自带jdk，这样做的好处是用户不需要再去单独安装jdk了，缺点是整个下载包变得很大，jdk占了一半以上    
logs：日志文件，排查问题的时候需要，例如慢查询日志elasticsearch_index_search_slowlog.log   

启动es   
es本身就是个java进程，./elasticsearch命令就可以启动es进程。但如果我们使用root用户启动会失败，es会提示不允许使用root用户启动，这是出于安全考虑，root拥有整个系统的生杀大权。   
使用如下命令解决   
```
groupadd es --创建一个es组
useradd es -g es --创建es组下一个es用户
chown -R es:es elasticsearch7.0 --为我们的目录赋予权限  
su es --切换到es用户
./elaseticsearch --启动es   
```   

启动后我们可以curl localhost:9200，得到如下结果，证明es已经启动成功。9200是es对外的restful api端口    
这里可以看到es的版本，集群的名称(单个节点也是一个集群)     
![iamge](3)   

## 安装kibana   
kibaa是elasic stack中的一员，用于可视化展示es中的数据，也可以在kibana上执行restful命令，操作es。   
同理到官网下载kibana7.10.0，kibana的目录结构和es类似，就不多介绍，我们同样用es用户运行kibana，kibana运行在5601端口。   
同样使用./kibana启动，kibana.yml server.host默认配置是注释掉的，说明外网是无法访问的，我们在本地虚拟机需要外部访问需要设置server.host:"0.0.0.0"    
安装成功后，如上可以看到我们安装的es节点，里面有一个test_idx索引，点击索引名称可以查看索引的具体配置。kinana默认会连接本机的localhost:9200的es,如果是在多台机器上，需要修改为对应地址。     
![image](4)    

## 安装es集群   
上面我安装了单个节点的es，如果没有配置cluster.name，默认就是名称为“elasticsearch”的集群。为了实现高性能，高可用，生产环境我们通常会部署多个es节点。   
这里我们单机部署一个3个节点的es集群，除了修改配置文件，也可以使用启动命令来指定参数。   
```
./elasticsearch -Enode.name=node1 -Ecluster.name=myes
./elasticsearch -Enode.name=node2 -Ecluster.name=myes
```
备注  
es默认jvm参数(config/jvm.options)-Xms1g -Xmx1g，在生产环境我们需要配置得大一点，在本地测试如果内存不够es会启动失败，可以给虚拟机分多一点内存，或者调小这两个参数。   
node.max_local_storage_nodes 该参数默认值是1，表示一个机器只能启动一个es节点，启动多个会报错，这里我们需要设置为2    

运行成功后，我们再看下kibana   
对比上面的图，集群的状态从yellow变成了green，并且test_idx有了一个replicas，这是一个备份分配，es已经自动把主分片备份到另一个节点上了。   
也可以在kibana上使用命令GET /_cluster/health查看集群的健康状态，得到如下结果    
```
{
  "cluster_name" : "myes",
  "status" : "green",
  "timed_out" : false,
  "number_of_nodes" : 2,
  "number_of_data_nodes" : 2,
  "active_primary_shards" : 7,
  "active_shards" : 14,
  "relocating_shards" : 0,
  "initializing_shards" : 0,
  "unassigned_shards" : 0,
  "delayed_unassigned_shards" : 0,
  "number_of_pending_tasks" : 0,
  "number_of_in_flight_fetch" : 0,
  "task_max_waiting_in_queue_millis" : 0,
  "active_shards_percent_as_number" : 100.0
}
```

思考   
1.9200,9300 端口   
9200端口是es对外的restful接口端口，9300是es集群内部的tcp通信端口。上面我们并没有配置端口，在同个节点上es启动居然没有冲突？   
实际上es在启动的时候会自动检测，发现冲突了会在某个区间找一个可用的端口使用，如上第二个节点分配的是9201和9301端口。   
2.es多个节点是如何自动组成一个集群的    
关于集群的发现，选举等后端单独讲讲，这里只需要简单知道一下，我们配置了相同的集群名称，es会在本机寻找相同集群名称节点，相同名称的节点会加入组成集群。   
discovery.seed_hosts: 是相关配置参数，默认是127.0.0.1，就是在本机中发现，如果是在不同机器就需要配置一下对方的地址。早期的es版本不需要这个配置，只要相同名称就会自动加入，后来发现这个机制并不好，例如在开发测试环境大家可能都用默认的集群名称，导致互相加入集群影响彼此。    

## 安装cerebro   
cerebro是es一个管理工具，[到github下载](https://github.com/lmenezes/cerebro/releases)    
./bin/cerebro 启动cerebro，运行在9000端口   
配置config/application.yml，name是es集群的名称，这里是上面的“myes”
```
hosts = [
  {
    host = "http://localhost:9200"
    name = "myes"
  }
```
启动后即可选择myes集群，进入管理界面如图   
![image](6)   
可以看到集群有两个节点，node1的星是满的，表示该节点是master节点。test_idx主分片在node1上，虚线的0表示这个是replicas分片。切换到node tab可以看到每个节点资源的一些情况，还有更多功能，比如集群配置，创建索引等都可以在这里完成。    

## 总结   
如上我们完成了es的安装以及相关工具的安装，对集群有初步的认识，后面会深入学习相关知识。   










