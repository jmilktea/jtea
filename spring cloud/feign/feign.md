## 使用   
1. 引入依赖包
```
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
  </dependency>
```
2. @EnableFeignClients 开启客户端功能
3. 定义接口
```
@FeignClient(name = "provider1")
public interface FeignProvider {
    @RequestMapping(value = "/provide", method = RequestMethod.GET)
    String provide1(String id);
}
```
参考文档：https://cloud.spring.io/spring-cloud-openfeign/reference/html/

## 日志
有时候我们需要观察请求的详细信息，包括参数，返回值等，方便找出问题。使用工具的话可以使用fiddler进行抓包，但我们希望能在ide直接观察。feign默认不输出日志，这非常不利于排除问题。例如如下输出405错误，并不够直观。  
![image](https://github.com/jmilktea/jmilktea/blob/master/spring%20cloud/feign/images/nolog.png)    
我们需要如下两步开启日志：  
1. 注入log bean
```
@Bean
public Logger.Level level(){
    return Logger.Level.FULL;
}
```
2. 配置文件，com.**是feign类的全路径
```
logging:
  level:
    com.jmilktea.service.feign.client.FeignProvider: debug
```
效果如下：  
![image](https://github.com/jmilktea/jmilktea/blob/master/spring%20cloud/feign/images/log.png)  

## 拦截器
拦截器允许我们对所有的feign或者特定的feign做一些全局处理，如添加请求头等。如下列举3种方式
1. 基于java配置。需要注意的是，如果加上FeiginInterceptor的@Configuration会是一个全局的拦截器，也就是对所有feign client都生效。如果不加上，则通过指定的feign的configuration配置生效。  
```
//@Configuration
public class FeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate requestTemplate) {
        requestTemplate.header("name", "test");
    }
}
@FeignClient(name = "provider1", url = "localhost:8081", configuration = FeignInterceptor.class)
public interface FeignProvider {

    @RequestMapping(value = "/provide", method = RequestMethod.GET)
    String provide1(String id);
}
```
2. 基于配置文件，provider2是fegin的name  
```
feign:
  client:
    config:
      provider2:
        requestInterceptors:
          - com.jmilktea.service.feign.client.FeignInterceptor
```
3. 基于feign builder配置  
```
    @Bean
    public FeignProvider3 feignProvider3() {
        return Feign.builder().contract(new SpringMvcContract()).requestInterceptor(new FeignInterceptor()).target(FeignProvider3.class, "localhost:8081");
    }
```

## 超时配置  
生产环境接口的超时设置是必须的，否则可能由于接口超时导致调用方请求积压，从而导致雪崩效应，拖垮整个上下游服务。feign支持如下3种超时配置。
- feign
```
feign:
  client:
    config:
      default:
         connectTimeout: 2000
         readTimeout: 2000
```
default表示默认配置，针对所有服务生效。如果针对某个服务，可以配置具体名称。
- hystrix
```
feign:
  hystrix:
    enabled: true
hystrix:
  command:
    default:
      execution:
        timeout:
          enabled: true
        isolation:
          thread:
            timeoutInMilliseconds: 2000
```
hystrix超时设置默认是关闭，需要开启。
- ribbon  
```
ribbon:
  ConnectTimeout: 2000
  ReadTimeout: 2000
  MaxAutoRetries: 0
  MaxAutoRetriesNextServer: 0
```
feign使用ribbon做客户端的负载均衡，当配置了@FeignClient的url属性时，ribbon的配置不会生效。   

这三个超时时间的配置有如下特点：
1. 配置@FeignClient的url属性，ribbon配置不会生效。hystrix和feign的超时，哪个超时配置小使用哪个
2. 配置@FeignClient的name属性，如果hystrix超时较小，则使用。否则如果配置了feign的超时时间，则使用（忽略ribbon）。否则使用ribbon的超时时间。

## 重试机制  
网络环境是不稳定的，有时候会发生抖动，导致请求异常。重试机制可以在网络发生异常导致建立链接超时、失败，或者链接读取数据超时等错误时，进行重试。和超时配置类似，可以使用feign的重试机制，也可以使用ribbon的重试机制。
- feign重试机制  
默认情况下，feign是不开启重试机制的。可以自己注册一个Retryer
```
@Configuration
public class SimpleRetryConfiguration {

	@Bean
	public Retryer retryer() {
    //2s内重试两次
		return new Retryer.Default(100, 2000, 2);
	}
}
```
这样配置会使所有feign client都生效，如果要针对某些client生效，可以注释掉@Configuration，并在具体的feign client指定配置
```
@FeignClient(name = "provider2", url = "localhost:8081", configuration = SimpleRetryConfiguration.class)
public interface FeignProvider2 {
}
```  
使用feign配置有几点需要注意：  
1. POST请求也会重试，如果接口没有保证幂等，可能会有问题  
2. 如果同时配置了hystrix超时时间，并且比feign的超时时间小，那么请求超时时会被hystrix熔断，不会有重试的机会
- ribbon重试机制  
和超时配置类似，重试机制也可以使用ribbon的重试机制。主要有如下配置：
```
ribbon:
  ConnectTimeout: 1000 
  ReadTimeout: 1000
  MaxAutoRetries: 0
  MaxAutoRetriesNextServer: 1  
  OkToRetryOnAllOperations: false
```
这样配置会使所有feign client都生效，如果要针对某些client生效，可以指定name
```
feign-name:
  ribbon:
```
使用ribbon配置有几点需要注意：
1. 配置了url的属于外部接口，此时请求不会经过ribbon，所以不会生效  
~~2. 配置了feign超时会覆盖ribbon超时，所以不会生效~~  
2. hystrix的超时时间要大于ribbon的超时时间，否则不会生效  
3. 默认情况下POST请求不会重试
4. OkToRetryOnAllOperations表示所有失败都重试，此时POST请求失败也会重试

## 连接池  
和其它池化的思想一样，使用连接池的目的是将链接缓存起来，减少链接的重建。默认情况下，feign使用的是HttpURLConnection，每个请求都会重新建立链接，效率较低。feign支持配置apache httpclient 和 okhttp 链接池，这里已httpclient为例。  
只需要引入如下包就会使用httpclient
```
<dependency>
  <groupId>io.github.openfeign</groupId>
  <artifactId>feign-httpclient</artifactId>
</dependency>
```  
常用配置如下：
```
feign.httpclient.enabled=true #是否开启httpclient
feign.httpclient.connection-timeout=2000 #链接超时时间
feign.httpclient.max-connections=200 #链接池链接最大链接数
feign.httpclient.max-connections-per-route #单个host最大链接数量 
feign.httpclient.time-to-live=900 #链接在池中的时间，默认是900s
feign.httpclient.connection-timer-repeat=3000 #链接池管理定时器执行频率
```
