## 简介  
[canal(水道/管道/沟渠)](https://github.com/alibaba/canal)是阿里开发的一个用于解析数据库日志，获取增量变更的一个组件。实际工作中会有许多需求需要和操作数据库数据一起执行，如缓存刷新，数据变更进行通知等，这类似于一个触发器的作用，当数据发生变更时，做某些处理。通常我们可以在程序里操作数据时一并进行其它操作，canal则提供了另一种思路，由数据变更来实现通知。还有数据迁移，数据备份，数据同步的场景，canal也可以很好的提供支持。canal目前主要支持的是mysql数据库。  

## 原理  
简单的说就是利用了mysql的主从同步机制，canal伪装成一个slave，从master获取日志。  
- mysql主从复制原理  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/canal/images/mysql-master-slave.png)  
1. master将改变记录到二进制日志(binary log)中（这些记录叫做二进制日志事件，binary log events，可以通过show binlog events进行查看）；
2. slave将master的binary log events拷贝到它的中继日志(relay log)；
3. slave重做中继日志中的事件，将改变反映它自己的数据。

- mysql binlog  
**相关配置：**    
**log-bin=mysql-bin** binlog文件命名前缀，如mysql-bin.000001  
**expire_logs_day=15** binlog过期时间，为了防止占用太多磁盘空间，可以设置一个过期时间  
**binlog-format=Row** binlog记录模式  
**server_id=1** MySQL replaction需要定义，master和slave不能重复，对于canal来说，会自动生成一个，可以通过**show slave hosts**查看

mysql的binlog是多文件存储的，可以通过**show binary logs**查看当前有哪些binglog文件，如：  
![iamge](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/canal/images/show-binlog.png)    

binlog文件的生成有三种方式：statement,row和mixed，不同模式下binlog记录的内容不相同。可以通过**show variables like 'binlog_format';**查看当前使用哪种模式，[详细参考](https://dev.mysql.com/doc/refman/5.7/en/binary-log.html)**
可以通过mysqlbinlog来查看binlog的内容，如：**mysqlbinlog --base64-output=decode-rows -v --start-datetime="2020-06-04 07:21:09" --stop-datetime="2020-06-05 07:59:50" mysql-bin.000003** --base64-output=decode-rows用来解码，默认binlog的sql语句是编码后的；--start/stop-datetime指定一个时间范围，也可以根据位点位置来筛选。  
- statement  
基于sql语句。由于只是简单的记录sql语句，所有存储空间较小，但是不能详细反映行的变化。如：  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/canal/images/binlog-statement.png)  
- row
基于数据行的变化。会详细记录数据行的信息，占用空间较大。如：  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/canal/images/biinlog-row.png)  
- mixed  
混合模式。一般基于statement，不满足时切换为row

- canal实现原理  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/canal/images/canal-slave-2.png)  
1. canal模拟mysql slave的交互协议，伪装自己为mysql slave，向mysql master发送dump协议
2. mysql master收到dump请求，开始推送binary log给slave(也就是canal)
3. canal解析binary log对象(原始为byte流)

## 架构
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/canal/images/canal-frame.png)  
说明：  
server代表一个canal运行实例，对应于一个jvm，就是一个进程  
instance对应于一个数据队列，1个server对应1..n个instance，不同instance可以有不同配置,也有一份公用的配置    
instance模块：  
eventParser (数据源接入，模拟slave协议和master进行交互，协议解析)  
eventSink (Parser和Store链接器，进行数据过滤，加工，分发的工作)  
eventStore (数据存储)  
metaManager (增量订阅&消费信息管理器)    
[详细参考](https://github.com/alibaba/canal/wiki/%E7%AE%80%E4%BB%8B)   
- 目录介绍  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/canal/images/canal-floder.png)   
- server-client  
canal是server-client模式，canal server将binlog解析，加工后，可以把转换好的数据发生给client处理，client可以是mq或者自己实现的程序。在没有与client建立联系的情况下，canal server是不会去消费binlog的，所以canal server不需要存储binlog数据，只要消费位点就行了，即知道下一次从哪里开始消费。    
- 存储  
对于canal server来说，主要存储[解析位点]和[消费位点]的位置，即上一次binlog消费到什么位置和client消费到什么位置。这个数据可以存储在本地文件或者zookeeper上，默认情况是本地存储，不支持HA。
