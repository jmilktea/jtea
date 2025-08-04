# 背景
这是去年做的事情了，去年九月我们将一个系统的网关zuul平滑升级为spring cloud gateway，在此记录一下升级方案，有相同需求的朋友可以做个参考。    

**升级原因：**     
1、之前我们升级了spring boot/cloud版本，网关模块没有升级，一直使用旧版本，不统一，公共包的管理和代码不好维护。   

2、低版本的spring cloud 使用zuul 1.x作为网关，zuul 1.x使用的是同步阻塞的serlvet线程模型，处理请求能力薄弱，容易出现线程膨胀问题。    
例如我们配置了ribbon.MaxHttpConnectionsPerHost = 600，即每个host会开600个线程处理请求，当请求越多时，就需要开更多的线程支持，而线程是占用资源的，在并发高的时候会造成机器负载高，线程切换频繁，gc频繁等问题。     
尽管zuul 2.x开始支持异步请求，但spring cloud并没有集成计划，而是推出了自家的网关spring cloud gateway。    

3、低版本的网关Netflix不再维护，有bug无法解决，同时在一些组件上支持不好，例如不能很好的整合websocket，resilience4j，redis ratelimit等组件。     

4、网关作为流量入口，高性能是基本要求，zuul已经不适用，现有使用spring cloud框架，spring cloud gateway是首选。    

因此我们决定对网关模块进行升级。    

# 原理分析
## 简介
spring cloud gateway是基于webflux框架构建的，功能丰富，高性能的，响应式网关。   
webflux是spring5推出的响应式web服务，与之前的spring mvc对比，传统的servlet是阻塞的。官网介绍如下：   

![image](1)   

https://spring.io/reactive/

可见spring并没有打算用reactive替换传统的servlet框架，而是两个分支发展，但毫无疑问，未来的重心发展在reactive stack。     
当然，现在看起来，reactive很可能遭受的挑战是虚拟线程，它比reactive更轻量，性能、可读性、调试都更优秀。       

两者也公用了一些基础组件，对于开发者来说，@Controller，@RequestMapping等使用和spring mvc是一样的。    
需要注意的是，响应式web服务并不能降低请求处理时间，例如一个请求本应该就要消耗1s，在webflux框架下，时间不会减少。      
**响应式服务的重点是：用较少的线程，通常是cpu的核数，处理更多的请求，提升吞吐量。**     

> 上面提到的reactive，有一个标准，叫做reactive stream：Reactive Streams is an initiative to provide a standard for asynchronous stream processing with non-blocking back pressure。参考：https://en.wikipedia.org/wiki/Reactive_Streams    
这个标准由Netflix, Pivotal and Lightbend发起，其中Pivotal就是开发spring的公司。         
project reactor是基于这个标准实现的类库，webflux是构建在reactor上的web服务。jdk9中对也对这个标准进行实现，提供了Flow接口。     

## 生产者-消费者模型
project reactor是基于生产者-消费者模型，生产者负责生产数据，消费者通过订阅，可以处理生产者生产的数据，并可以在完成和出错时做出响应。     

顶层Publisher接口：   

![image](2)   

顶层Subscriber接口：   

![image](3)   

**Mono和Flux**是两个最常用的生产者，我们平时使用的几乎都是它们，Mono表示生产0或1个元素的生产者，Flux表示生产0至N个元素的生产者，可以简单理解为Object和List。   

如下示例：定义一个包含1,2,3,4的Flux，然后map定义一个方法，将每个元素*2，然后定义一个消费者，打印结果。    
```
Flux.just(1, 2, 3, 4)
      .map(s -> s * 2)
      .subscribe(s -> System.out.println(s));
```      
看起来和java8里的stream集合操作和相似，它们都是在生产数据，然后定义处理数据的流程（过滤，加工），最后进行消费，同样是在消费时才会触发前面的一系列操作。

## 传统servlet vs webflux
传统servlet采用的是一个servlet一个线程的处理方式，这种模式在线程数少的cpu密集型服务下不会有多少问题，但一旦遇到IO，线程就会挂起，等待IO返回，此时线程什么事情都做不了，只能干等待。    
遇到这种问题，一般我们的做法就是增加处理线程，但没有免费的午餐，增加线程会增加资源消耗，每个线程都可以申请占用1M的栈空间，和少量的内核空间，同时更多的线程会带来线程切换，也会有性能损耗。    
而一旦servlet容器的线程被使用完了，请求就不得不排队，进入队列，尽管cpu此时是空闲的，但得不到任何利用。     

![image](4)     

servlet 3.0后开始支持非阻塞，tomcat等常用容器都支持servlet3.0。      
与之相比基于响应式的webflux框架是非阻塞的，这样线程可以立马返回，处理其它任务，而当IO返回，如读取数据库完成时，响应式框架会通知我们，线程接着处理返回的数据。

![image](5)     

从图可以看到，通过事件的方式将同步变成异步，请求只需要将阻塞操作提交给Event Loop就可以返回处理其它请求，当操作返回时，EventLoop会通知线程继续处理，这样一个线程就可以处理很多个请求。   

熟悉Linux IO多路复用模型的同学对这种方式肯定很熟悉，思想上是一样的。     
webflux 需要使用非阻塞的容器，如：netty，tomcat等都可以，默认使用的是netty，服务启动后可以看到：o.s.b.web.embedded.netty.NettyWebServer  : Netty started on port 18001       
netty是一个高性能、易扩展、社区活跃的网络开发框架，已经过大量的生产验证，ElasticSearch、Dubbo、Rocketmq、HBase、spring webflux，gRPC都使用了netty作为底层网络开发框架。     

**注意：**既然使用了响应式框架，意味着只有少量处理请求的线程，请求从头到尾就不能有阻塞操作，否则请求线程很快会消耗完。

正例：web 请求 → 查接口（非阻塞）→ 处理返回数据 → 查数据库（非阻塞）→ 处理数据     
反例：web 请求 → 查接口（非阻塞）→ 处理返回数据 → 查数据库（阻塞）→ 处理数据      

幸运的是现在基本所有的阻塞IO操作都有相应的reactive实现，如Feign → ReactiveFeign，Redis → ReactiveRedis，jdbc → r2dbc。      

## springcloud gateway处理请求流程
![image](6)      

- global filter，实现GlobalFilter接口，拦截所有请求
- gateway filter，实现GatewayFilter接口，拦截指定的路由请求

# 功能调整    
## 3.1 配置调整
3.1.1
```
server:
  port: 8001
  tomcat:
    max-threads: 5000

->

server:
  port: 8002
```
说明：使用8002端口，与8001会有一段时间并行运行，等验证切换正常，下掉8001服务，详细见下面上线方案。

3.1.2
```
spring:
  application:
    name: gateway
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
      enabled: true

->

spring:
  application:
    name: gateway
```
说明：servlet配置对webflux不再适用，gateway下，网关不再需要配置请求大小，由ng和后端服务决定。

3.1.3
```
management:
  endpoints:
    web:
      exposure:
        include: health, prometheus
```   
说明：不需要调整，端点测试正常。   

3.1.3
```
ribbon:
  MaxAutoRetries: 0
  MaxAutoRetriesNextServer: 1
  OkToRetryOnAllOperations: true
  retryableStatusCodes: 500,503
  ReadTimeout: 30000
  ConnectTimeout: 1000
  MaxHttpConnectionsPerHost: 200
  MaxTotalHttpConnections: 5000
  ServerListRefreshInterval: 12000
  restclient:
    enabled: true

->

spring: 
  cloud:
    loadbalancer:
      retry:
        retryableStatusCodes: 500,503
        retryOnAllOperations: true
      cache:
        enabled: false
	gateway:
	  httpclient:
	    connect-timeout: 1000
    	response-timeout: 30000
```
说明：gateway下ribbon已经废弃，使用loadbalancer。    
cache.enabled: false 禁止loadbalancer缓存，避免双缓存，使用eureka client缓存即可。     

3.1.4
```
hystrix:
  command:
    default:
      fallback:
        isolation:
          semaphore:
            maxConcurrentRequests: 500

->
```
说明：删掉，gateway下hystrix已经废弃。    

3.1.5
```
zuul:
  semaphore:
    max-semaphores: 5000
  retryable: true
  routes:
    data:
      stripPrefix: false
      path: /data/service/**
      serviceId: data-server

->

routes:
  - id: data-server
    uri: lb://data-server
    predicates:
      - Path=/data/service/**
    filters:
      - name: CircuitBreaker
``` 

3.1.6
```
eureka:
  client:
    registry-fetch-interval-seconds: 13

->

eureka:
  client:
    registry-fetch-interval-seconds: 25
```

## 3.2 阻塞代码改写     
3.2.1 feign
openfeign并没有提供reactive的实现，而是推荐使用第三方的：https://github.com/PlaytikaOSS/feign-reactive， 这是一家游戏公司开源的。     
相关issues：https://github.com/spring-cloud/spring-cloud-openfeign/issues/668#issuecomment-1607854972。     
使用方式如下，ReactiveFeignClient标记Feign接口，接口方法返回值必须是Mono或者Flux。    
```
<dependency>
    <groupId>com.playtika.reactivefeign</groupId>
    <artifactId>feign-reactor-spring-cloud-starter</artifactId>
    <version>3.2.11</version>
</dependency>
```
```
@EnableReactiveFeignClients
public class SpringCloudGatewayApplication{}

//定义feigin
@ReactiveFeignClient(name = "data-server")
public interface DataClient {

   @GetMapping("/user")
   Mono<List<String>> user(@RequestParam("uid") Long uid);
}
```
3.2.2 redis
使用非阻塞的ReactiveRedisTemplate。    
```
@Autowired
ReactiveRedisTemplate reactiveRedisTemplate;
```   
3.3 其它    
3.3.1 session问题    
webflux使用的是WebSession，redis session使用的是EnableRedisWebSession。    
sessionId问题需要重写一下解析sessionId的方法，保证传到下游服务的sessionId一致，参考：https://juejin.cn/post/7181636384979943481。     

3.3.2 国际化问题  
现有i18n工具类，获取国际化信息，LocaleContextHolder内部使用了ThreadLocal，ThreadLocal在webflux中不适用，需要改写。    
可以通过exchange.getRequest().getHeaders().getAcceptLanguageAsLocales()拿到当前语言的Locales对象。    
```
public static String get(String key, String defaultMessage) {
    return messageSource.getMessage(key, new Object[]{defaultMessage}, defaultMessage, LocaleContextHolder.getLocale());
}
```
3.3.3 全局异常处理   
原有的GlobalFallbackProvider和ErrorFilter已经不适用，使用ErrorWebExceptionHandler。    
```
@Slf4j
@Component
@Order(-1)
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

   @Override
   public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
	  //handle exception
      return null;
   }
}
```    
3.3.4 日志      
必须使用AsyncAppender异步写入方式，在响应式的世界里，所有涉及到io的都必须非阻塞。      
3.3.5 不支持     
spring redis SessionCreateEvent等事件在WebSession不被支持，无法使用。    

# 四、压测     
部署环境：4C32G   
服务部署：单机   
jvm参数：-Xms1g -Xmx1g    
压测时间：3min，压测线程30s内启动完成    
压测超时时间：2s   
压测接口：接口20%的时间为100ms，80%的时间为50ms     

## 4.1 zuul
**线程数：200**    
执行情况：失败率：0，P99：388，吞吐量：1403     
![image](7)    

gc情况：2秒左右一次young gc，无full gc（超过3分钟没观测到）  
![image](8)    

线程情况：大量线程  
![image](9)    

cpu情况：cpu负载14,使用率60%  
![image](10)    

**线程数：600**     
执行情况：失败率：0.01，P99：810，吞吐量：1952	  
![image](11)    

gc情况：每秒一次young gc，每分钟一次full gc	  
![image](12)    

线程情况：大量线程	  
![image](13)

cpu情况：cpu负载77，使用率80%  
![image](14)

## 4.2 springcloud gateway
**线程数：200**    
执行情况：失败率：0，P99：403，吞吐量：1388  
![image](15)

gc情况：3秒左右一次young gc，无full gc  
![image](16)

线程情况：线程数稳定	  
![image](17)

cpu情况：cpu负载14，使用率50%  
![image](18)

**线程数：600**    
执行情况：失败率：0.02%，P99：787，吞吐量：2202  
![image](19)

gc情况：2秒左右一次young gc，无full gc   
![image](20)

线程情况：线程数稳定  
![image](21)

cpu情况：cpu负载28，使用率70%   
![image](22)


官网的benchmark：https://github.com/spencergibb/spring-cloud-gateway-bench  
![image](23)

## 总结      
使用springcloud gateway在并发增加时，线程数始终稳定，与cpu核数一致，图中的http-reactor线程，而zuul会创建大量线程。     
在并发高时，springcloud gateway对cpu的使用显要优于zuul，吞吐量也更好。springcloud gateway gc表现稍好。    
springcloud gateway请求时间并没有比zuul好，这也符合前面的原理分析，webflux接口相应时间并没有减少。      

# 上线方案
网关是所有流量的入口，为了避免新网关出现问题影响业务，需要平滑过度，新网关验证正常后，再将流量完全切换，下掉旧网关服务。     
发版时不能直接使用滚动发布，可以使用灰度发布或蓝绿发布，本次采用灰度发布的方式，切换过程要运维配合，需提前通知。    

## 方案一：灰度发布
逐个节点切换为新代码，部署时先部署1个节点，放少量流量，验证没问题，再部署其它节点。   
优点：不需要部署新服务，切换过程简单，不需要下线旧服务。   
缺点：切换验证过程，老网关节点压力会有较大压力。   
整体过程如下：   
![image](24) 

## 方案二：蓝绿发布    
部署一套新网关服务，放少量流量到新网关服务，验证没问题，直接下线老网关服务。     
优点：对老网关完全没有影响，不会增加节点压力。    
缺点：需要部署一套新服务，切换过程比较复杂，需要下线旧服务。    
整体过程如下：   
![image](25)    

## 回滚方案
验证过程发现有问题，通过ng切量回老网关。   
老网关代码master checkout一个分支保留，有问题可以随时回退到老代码。     

## 上线后问题   
1.熔断，https://cloud.spring.io/spring-cloud-gateway/reference/html/#spring-cloud-circuitbreaker-filter-factory   
熔断后默认抛出的异常不友好，无法看出是被熔断了，可以重写其逻辑。   

2.reactive feign超时设置，没有application直接配置方式，代码配置。https://github.com/PlaytikaOSS/feign-reactive/tree/develop/feign-reactor-spring-configuration

3.注意Mono/Flux写法，避免嵌套太深。1.抽取方法 2.流式写法。   

4.url编码问题，解密接口前端进行了两次编码，在zuul没有问题，zuul会decode一次，进入后端服务，spring mvc会decode一次，gateway则没有decode，导致到后端服务只decod一次，报错。

5.参数调优    
目前的机器配置，connect-timeout设置为2s时，在请求量大时会出现connection timeout exception，原因是处理连接的线程能力不足，将其设置到5s。这会导致量大时请求时间增加，可以通过增加机器解决，不过只有一瞬间出现，暂时忽略。或可以尝试设置netty IO_SELECT_COUNT为比较大的值，这个会增加线程成本。    
上线后出现一些连接提前关闭的错误，例如connection reset by peer， Connection prematurely closed BEFORE response，原因是gateway对连接缓存，但下游服务在一定时间后会关闭连接，导致用了一个已关闭的链接。设置spring.cloud.gateway.http-client.pool.max-idle-time为15s，连接空闲15s后关闭。         