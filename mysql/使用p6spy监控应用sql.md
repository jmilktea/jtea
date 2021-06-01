## 简介
我们的应用在访问数据库时都会使用数据库连接池，对连接进行池化管理，避免频繁创建和消耗连接带来的性能损耗。常用的连接池有hikari,druid，c3p0等，druid在阿里的推行下在国内非常流行，并且拥有比较好的扩展和监控功能，详细参见：[druid](https://github.com/alibaba/druid)。springboot默认提供的是hikari，是一款非常轻量和高效的连接池，但是它并不像druid一样提供了一个管理端的console对sql进行统计分析。如果我们使用hikari，并且需要打印sql，或者分析一下慢sql，可以使用[p6spy](https://github.com/p6spy/p6spy)，它可以很方便的集成到我们的程序，并且对sql进行拦截，打印等。  
实际生产上我们不会打印所有sql，或者有个开关，必要的时候才会打印排查问题。生产环境主要关注的是慢sql，我们经常需要把慢sql抓出来优化，避免几个慢sql就拖垮整个库。当然数据库本身也有慢sql日志，也可以在db端拿到这些慢sql，不过一般开发没有数据库权限，需要找dba抓取。开发和测试环境一般会打印所有sql，方便观察。  

## 使用  
导入包  
```
<!-- https://mvnrepository.com/artifact/p6spy/p6spy -->
<dependency>
    <groupId>p6spy</groupId>
    <artifactId>p6spy</artifactId>
    <version>3.9.1</version>
</dependency>
```

修改datasource配置为p6spy
```
url: jdbc:p6spy:mysql://...
driver-class-name: com.p6spy.engine.spy.P6SpyDriver
```

在resources资源文件下新增spy.properties配置p6spy属性，这里我们配置超过1s的sql打印出来
```
# 实际驱动
driverlist=com.mysql.jdbc.Driver
# 超过这个值才打印
executionThreshold=1000
# 日志格式
appender=com.p6spy.engine.spy.appender.Slf4JLogger
logMessageFormat=com.p6spy.engine.spy.appender.CustomLineFormat
customLogMessageFormat=sql:%(sql) | use:%(executionTime) ms
```

输出结果
```
2021-06-01 11:56:06.041 [main] INFO  [-] [p6spy] [Slf4JLogger.java:60] - sql:SELECT*FROM table1 totdfl
  INNER JOIN table1 totdfl1 ON totdfl.id = totdfl1.id
  INNER JOIN table1 totdfl2 ON totdfl.id = totdfl2.id
  INNER JOIN table1 totdfl3 ON totdfl.id = totdfl3.id
  WHERE name = 'abc'; | use:1522 ms
```

更多完整的配置：[参考](https://p6spy.readthedocs.io/en/latest/configandusage.html)    
这个配置在springboot用起来不是特别方便，我们使用它包装的[starter](https://github.com/gavlyukovskiy/spring-boot-data-source-decorator)，可以方便集成。   
它打印出来的是完整的sql，没有包含占位符，可以直接拿来执行。  
使用起来还是比较简单的，但功能不够完善，一般需求不高的场景下可以使用，mybatis plus也集成了这个组件用于sql打印分析。  


