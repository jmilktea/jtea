参考文档：https://cloud.spring.io/spring-cloud-openfeign/reference/html/

## 日志
有时候我们需要观察请求的详细信息，包括参数，返回值等，方便找出问题。使用工具的话可以使用fiddler进行抓包，但我们希望能在ide直接观察。feign默认不输出日志，这非常不利于排除问题。例如如下输出405错误，并不够直观。  
![image](https://github.com/jmilktea/jmilktea/blob/master/spring%20service/feign/images/nolog.png)    
我们需要如下两步开启日志：  
1.注入log bean
```
@Bean
public Logger.Level level(){
    return Logger.Level.FULL;
}
```
2.配置文件，com.**是feign类的全路径
```
logging:
  level:
    com.jmilktea.service.feign.client.FeignProvider: debug
```
效果如下：  
![image](https://github.com/jmilktea/jmilktea/blob/master/spring%20service/feign/images/log.png)  
