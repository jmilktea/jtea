## 简介
通过[canal简介]()可以有一个基础的了解，按照学习方法，本篇我们把它用起来，实现将订阅后的信息发送到rocketmq。可以参考：[官方quick start](https://github.com/alibaba/canal/wiki/Canal-Kafka-RocketMQ-QuickStart)  

## 实现  
- mysql 配置  
首先配置一下mysql，开启row默认的binlog。找到my.cnf，加入如下配置：  
```  
log-bin=mysql-bin # binlog文件名称前缀
binlog-format=Row # 选择 Row 模式
server_id=1 # 配置 MySQL replaction 需要定义，不要和 canal 的 slaveId 重复
```   
可以通过locate my.cnf快速找到my.cnf文件位置，修改后，使用 service mysqld restart 重启一下mysql  

创建个账号给canal server访问mysql  
```
CREATE USER canal IDENTIFIED BY 'canal@Canal123';  
GRANT SELECT, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'canal'@'%';
FLUSH PRIVILEGES;
```

- canal配置  
找到canal.properties配置，主要关注如下配置：
```
canal.destinations = example #当前server部署的instance列表，多个用,分隔，需要在conf建同名目录  
canal.instance.global.spring.xml = classpath:spring/file-instance.xml #配置，默认是使用file文件模式
canal.serverMode = rocketmq #使用rocketmq
canal.mq.servers = rocketmqnamesrvip:9876 #rocketmq name server地址
canal.mq.producerGroup = test #producer group
```
找到example/instance.properties，主要关注如下配置：
```
canal.instance.master.address=127.0.0.1:3306 #mysql地址
canal.instance.dbUsername=canal #账号
canal.instance.dbPassword=canal@Canal123 #密码
canal.instance.filter.regex=.*\\..* #过滤规则，例如只同步某些表，默认是所有
canal.mq.dynamicTopic=account\\..* #动态topic，如这里是account_表名
canal.mq.partitionHash=.*\\..*:$pk$ #hash规则，这里使用的是表主键进行hash决定是发送到哪个队列上。默认是都发送到0上
canal.mq.partitionsNum=16 #mq partition数，也是用来决定消息是发送到哪个partition上
```

- 启动canal  
./startup.sh，观察日志
![image]()  

- rocketmq观察消息  
要先按照规则创建对应的topic，如account_test，修改变触发binlog，可以观察到消息生成  
![image]()  
消息内容解释：
```
{
    "data":[ #数据
        {
            "id":"35",
            "name":"tom",
            "uid":"28"
        }],
    "database":"account", #db name
    "es":1591257157000, #binlog生成的时间戳
    "id":494, #序号
    "isDdl":false, #是否ddl
    "mysqlType":{ #表字段类型
        "id":"bigint(20)",
        "name":"varchar(50)",
        "uid":"bigint(20)"
    },
    "old":[ #修改字段旧值
        {
            "uid":"27"
        }],
    "pkNames":[ #主键名称
        "id"],
    "sql":"", #sql
    "sqlType":{ #字段对应的jdbc类型
        "id":-5,
        "name":12,
        "uid":-5
    },
    "table":"test", #表名称
    "ts":1591257207256, #canal获取到数据的时间戳
    "type":"UPDATE" #sql类型
}
```


