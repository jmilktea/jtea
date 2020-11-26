java开发规范已经有一些比较好的定义，收集如下：  
[阿里java开发规范](https://github.com/alibaba/p3c/blob/master/Java%E5%BC%80%E5%8F%91%E6%89%8B%E5%86%8C%EF%BC%88%E5%B5%A9%E5%B1%B1%E7%89%88%EF%BC%89.pdf)  
[google java style](http://hawstein.com/2014/01/20/google-java-style/)  
基于这些规范，我们定义一些推荐的做法，欢迎补充

## 基础类
- 习惯格式化代码  
说明：提高可阅读性。使用idea可以在提交代码时勾选【reformat code】格式化代码，避免忘记。也可以安装【save action】插件，可以在编译和保存文件时自动格式化

- 必要的注释  
说明：核心逻辑，特殊逻辑，复杂逻辑都应该加注释。注释不是碎碎念，不要啰嗦

- 使用卫模式/三元表达式简写if-else
```
if(a == 1){
    return "yes";
} else{
    return "no";
}
//to
if(a == 1){
    return "yes";
}
return "no";
//to
return a == 1 ? "yes" : "no";
```

- 方法返回空集合而非null  
说明：返回空集合，这样方便调用者直接操作，如直接添加到另一个集合中，而不用判空

- 数字引用类型与数字==判断，注意空异常  
说明：将魔数定义成Integer常量，常量.equals()判断，或者定义成枚举  
```
Integer type = null
if(null == 1) {
    //NullPointerException
}
```

- 使用不可变集合    
说明：如Map初始后不考虑修改，就是不可变的
```
//java8推荐使用guava
Map map = ImmutableMap.of("name","tom","age","1");
//java9开始可使用类型的静态方法
Map map = Map.of("name","tom","age","1");
```

- Integer==比较问题
```
System.out.println(Integer.valueOf(100) == Integer.valueOf(100)); //true
System.out.println(Integer.valueOf(128) == Integer.valueOf(128)); //false
```
说明：对于Integer==比较的是地址，默认情况下Integer会通过IntegerCache缓存-128~127的整数，所以第一个能返回true。
不在这个范围的，都是不同对象，此时比较值应该使用equals方法

- 考虑幂等性  
说明：post接口，mq消息消费都需要考虑幂等，防止重复

- 禁止在事务内做耗时操作  
说明：在事务内做接口调用，中间件调用等，会延长事务和锁的持有时间，影响db并发效率。[参考](https://github.com/jmilktea/jmilktea/blob/master/%E8%AE%BE%E8%AE%A1/%E5%88%86%E5%B1%82%E8%AE%BE%E8%AE%A1.md)  

- springboot配置文件   
    - bootstrap.yml放一些服务相关的，如server配置，spring配置，db配置，redis配置等
    - application.yml放应用，业务配置相关的，且是公共的，会被继承
    - application-dev.yml配置的是开发环境的，会覆盖bootstrap.yml或application.yml部分配置
    - application-test.yml配置的是测试环境的，会覆盖bootstrap.yml或application.yml部分配置
    - application-pre.yml配置的是测试环境的，会覆盖bootstrap.yml或application.yml部分配置
    - application-prod.yml配置的是生产环境的，会覆盖bootstrap.yml或application.yml部分配置
    - application-local.yml用于个人使用,方便本地调试，该文件不提交到git
    
举例：  
1. server.port -> bootstrap.yml
2. business.time_out -> application.yml 业务配置  
3. logging.level...XFeignApi:debug -> dev或test，这个配置不是公共的，只是为了开发，测试开启debug，如果配置在bootstrap.yml，prod忘记覆盖就会变成debug 

- 时间戳是没有时间的，时间是有时区的  
说明：时间戳表示格林威治时间1970年01月01日00时00分00秒起至现在的总秒数，它在地球的每个角落都是相同的。但相同的时间戳在不同地方有不同表现，所以有了时区。
例如东八区此刻的时间戳是1603497600000(通过System.currentMillis()获得)，那么同一刻，0时区获得也是该值。但在东八区它表示8点，在0时区它表示0点。

- 时间joda time来操作时间  
说明：封装得比较好，[参考](https://github.com/JodaOrg/joda-time)  

- 清理不需要的代码  
说明：除非能预料到后面会用到该代码，否则目前不需要的代码及时清理掉，避免项目越来越臃肿

- 保持逻辑严谨  
说明：条件判断几乎不消耗任何性能，不要写多一个if会影响效率   
```
//执行20w次时间消耗几毫秒
if(a == 1){
    return;
}
```

## 进阶类
- 接口调用设置超时时间  
说明：没有超时时间可能导致大量请求阻塞，最终耗尽资源

- 接口调用设置重试次数  
说明：网络是不稳定的因素，偶尔抖动应该重试，特别是get请求，应该支持重试    

- 使用连接池  
说明：没有使用连接池时，每个请求都需要进行一次完整的连接过程，使用连接池可以重用连接  

- 写日志使用异步的方式  
说明：同步每次都要写磁盘，效率低  

- 禁止在日志打印时序列化对象   
说明：有不少人喜欢这样打日志log.info("data:{}",JsonUtil.toJsonString(data));这样每次打印都会进行序列化，消耗cpu资源  
直接使用log.info("data:{}",data);即可  

- 日志应该尽可能详细  
说明：尽可能详细，方便排查问题  

- 日志压缩备份  
说明：日志文件可以采用按天备份的方式，减少文件体积，如果有收集到elk，本地的备份时间可以不用太长，一般3天就足够

- 日志观察  
说明：功能上线后要第一时间观察时间，保证没有异常日志，有问题再最短时间内修复

- 先更新数据库再删除缓存  
说明：使用cache aside pattern原则操作缓存和数据库，再加上过期时间，尽可能保证缓存和数据库的一致性  
cache aside pattern描述：  
1.读取：先读取缓存，存在直接返回。不存在读取数据库，设置缓存，返回  
2.更新：先更新数据库，再删除缓存  

## git
- 尽可能详细的commit log  
说明：方便后续回顾  

- 敏感信息不应该提交到git  
说明：如springboot生产环境的配置，不能配置到配置文件提交到git，如果代码被开发上传到github，那么信息就公开了

- 合并/提交代码前应该确保程序正常  
说明：在提交代码前应该确保程序编译正常，服务能正常运行，避免提交后被别人拉取，影响整个团队




