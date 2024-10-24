# 背景    
在[限流算法](https://github.com/jmilktea/jtea/blob/master/%E7%AE%97%E6%B3%95/%E9%99%90%E6%B5%81%E7%AE%97%E6%B3%95.md)这一篇中我们对限流算法以及常见的限流组件做了介绍，基本上对限流有个大概的认识了。这不，前段时间笔者就接到一个实际需求，一个项目要对某些请求进行qps限流，以保护后端的模型服务，正好把之前的理论做了实践，可见平时的知识储备是多么重要，正所谓厚积才能薄发。于是有了本篇，限流实战。   

# 技术方案
缓存、降级和限流作为高并发系统三驾马车，对系统保护起到至关重要作用。限流顾名思义是对流量大小进行限制，防止请求数量超过系统的负载能力，导致系统崩溃，起到保护作用。   
但具体到业务场景，限流还是很复杂的，不局域于如下场景：   
- 限制某个接口的并发请求数   
- 限制某个接口每秒最多访问多少次   
- 限制某个ip每秒最多访问多少次
- 限制某个用户或某个来源每秒最多访问多少次 
- 限制某些用户下载速度每秒最多多少kb    

## 限流分类
按照不同的逻辑，我们对限流进行如下分类，以便更好的理解如何具体实现。    

### 南北流量     
这里指的是流量的流向，在我们画架构图的时候习惯是从上往下（南北），再从左往右（东西）。    
我们既可以在入口处对请求进行限流以保护整个应用（南北），也可以在出口处进行限流保护下游应用（东西，下游应用可以是第三方服务，数据库等）。     

两者的粒度是不一样的，前者粒度比较粗，实现起来比较通用，我们本次是这种方式，在网关对指定的请求进行限流。后者粒度比较细，通常需要具体编码，例如可以使用guava，redisson等实现。    

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-1.png)    

### 限流类型
- 请求频率    
QPS或QPM，QPS比较常见，QPM会出现在一些第三方服务调用，例如google map api限制免费调用是每分钟3000次。       

- 并发限流    
QPS是指一秒内能处理的请求，而并发限流是只同一时刻能处理的请求。    
实现并发限流的主要实现方式有两种：1.使用线程池 2.使用信号量。

- 带宽限流  
例如会员下载速度会更快。     

### 限流维度
- 单机限流   
在单个节点（应用）上进行限流，实现简单、高效，适合比较简单的场景。但无法应用对复杂场景和对整个集群进行把控，当出现负载不均衡时，就会出现误判。    
例如限制每个节点QPS为100，当出现负载不均衡时，有的节点接收了110个请求将有10个请求被丢弃，实际集群是可以处理的。此外，负载均衡算法，例如hash，最小压力等算法会让单机限流变得复杂。  
单机限流实现方式：guava，resilience4j，sentinel。

- 集群限流    
相比单机限流较复杂，通常需要一个单独数据源存储数据，例如redis。   
集群限流实现方式：redission，bucket4j，sentinel，springcloud gateway RequestRateLimiter，nginx。

## 限流算法     
两桶两窗，前面文章已经介绍过，这里我们温习一下。   

### 固定窗口    
固定窗口的思想和实现非常简单，就是统计每个固定每个时间窗口的请求数，超过则拒绝。   

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-2.png)    

如图我们定义了窗口大小为1s，最大请求数100，窗口内超过100的请求数将被拒绝。实现上也非常简单，利用redis的incr自增计数，当前时间秒作为缓存key，每次自增后判断是否超过指定大小即可。  

**突刺现象**和**临界问题**     
在1s内，100个请求都是在前100ms过来的，那么后面的900ms的请求都会被拒绝，而系统此时是空闲的，这种现象称为“突刺现象”。
如果100个请求是在后100ms过来的，而下一个1s的100个请求在前100ms过来，此时系统在这200ms内就需要处理200个请求，这种现象称为“临界问题”。    

到这里我们很容易想到，1s这个范围太大了，缩小一些就更好了，这种把固定窗口拆成更多个小窗口的做法就是滑动窗口算法。     

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-3.png)   

### 滑动窗口
滑动窗口的思想是将固定窗口拆成更多个小窗口，随着时间的推移，窗口不断的滑动，统计也在不断的变化。窗口拆分的越多，滑动就会越平滑，统计就会越精确，所消耗的资源就会越多。若滑动窗口如果只拆为1个窗口，就退化为固定窗口。  

滑动窗口算法可以解决上面固定窗口的问题，像hystrix和sentinel中都使用该算法进行数据统计，用于限流熔断等策略处理。如hystrix中图所示，在一个窗口内拆分了10个桶（bucket），随着时间的推移，会创建新的桶也会丢弃过期的桶，窗口的大小和拆分桶的数量都是可配置的。  

图片来自hystrix官网：   

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-4.png)    

### 漏桶
漏桶算法的思想是将请求先放到一个桶中，然后像滴水一样不断的从中取出请求执行，桶满则溢，后面的请求会被拒绝。  
漏桶算法的特点是流入速度不确定，但是流出速度是确定的，漏桶可以很平滑，均衡的处理请求，但是无法应对短暂的突发流量。   

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-5.png)    

### 令牌桶
令牌桶算法的思想是不断的生成令牌放到一个桶中，请求到来时到桶中申请令牌，申请得到就执行，申请不到就拒绝。如果桶中的令牌满了，新生成的令牌也会丢弃。  

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-6.png)   

与漏桶不同的是，令牌桶是流入速度确定(生成令牌的速度)，流出速度不确定，所以它不像漏桶一样可以均衡的处理请求，但是由于有令牌桶这个缓冲，一旦有突增的流量，令牌桶里已有的令牌可以短暂的应对突发流量，由于流出速度是不限制的，此时桶中已有的令牌都可以被申请到，请求一下子就会到我们的服务，给系统带来一定的压力，所以桶的大小需要合适，不宜过大。    

举个栗子：令牌桶的大小是1000，每秒放100个令牌，经过一段时间后，请求有空闲时，桶里的令牌就会积压，最终保存了满1000个令牌，由于某刻流量突增，有1000个请求到来，此时能申请到1000个令牌，所有请求都会放行，最终达到我们的系统，如果令牌桶过大，系统可能会承受不了这波请求。

令牌桶算法是限流算法中应用最广泛的，几乎所有中间件都有它的实现。根据描述令牌桶的核心是要往桶里放入令牌，主要有两种实现思路：    
1、起一个线程定时放入令牌。   
2、取令牌时再计算要放入的令牌数，然后获取。    

第二种方式有“懒加载”的意思，例如使用redis实现的限流，我们不可能在redis server起一个线程，所以只能再获取令牌时再实时计算。     

## 限流处理
限流后如何处理也是我们需要关注的，一般有以下处理方案。   
1、返回http 429 too many request 错误码      
这是一种标准处理方式，我们在调用阿里云一些服务的时候如果被限流也会返回此错误码。     
2、重新包装为http 200 ok，返回提示文案   
有些人希望这样做，请求都返回200，具体错误信息放在一个“message”字段中，个人不是很推荐。  
3、降级   
调用方处理，例如跳转到一个通用界面。    
4、排队   
有时候我们不希望请求丢弃，例如下单场景，我们既希望下单请求不要太多，也不希望它失败，这种场景会将请求放入队列再处理。     

## 技术选型    
我们的限流业务背景是：已经使用了spring cloud gateway，限流场景简单，要求集群限流，没有并发限流要求，希望快速集成开发，易维护。  

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-7.png)    

综上，我们选择spring cloud gateway自带的RateLimiter实现，底层使用的是令牌桶算法，[官方文档](https://docs.spring.io/spring-cloud-gateway/docs/3.1.4/reference/html/)        
注意，RateLimiter不支持：  
1、并发限流。  
2、小数位限流，例如：6次/min 这种是不支持的。     
3、自定义响应码和返回报文。    

### 例子      
RequestRateLimiter就是RequestRateLimiterGatewayFilterFactory，是一个gateway filter，意味着它需要配置在route上。   
示例：   
```
routes:
  - id: order-service
    uri: lb://order-service
    predicates:
      - Path=/order/**
    filters:
      - name: RequestRateLimiter
        args:
          redis-rate-limiter.requestToken: 1
          redis-rate-limiter.replenishRate: 100
          redis-rate-limiter.burstCapacity: 100
          key-resolver: "#{@urlKeyResolver}"      
```
以上配置表示限制100qps，参数解释：
- requestToken，每次请求申请的令牌数，默认是1。
- replenishRate，每秒放入桶里的令牌数。
- burstCapacity，桶大小。
- key-resolver，用于缓存限流标识的key解析对象，可以使用spring bean语法配置。   

KeyResolver接口，返回一个String给缓存使用，例如基于url的限流可以返回request相对路径。    
如果resolve方法返回空，默认是返回403，可以配置deny-empty-key=false，返回空时直接放行，防止代码、数据等异常导致大量报错。    
```
public interface KeyResolver {
   Mono<String> resolve(ServerWebExchange exchange);
}

@Bean
public KeyResolver urlKeyResolver() {
    return exchange -> Mono.just(exchange.getRequest().getMethod() + "/" + exchange.getRequest().getPath());
}
```
```
spring.cloud.gateway.filter.request-rate-limiter.deny-empty-key = false
```

### 源码分析   
RequestRateLimiter实际就是RequestRateLimiterGatewayFilterFactory，省略GatewayFilterFactory是“约定大于配置”。
代码入口在org.springframework.cloud.gateway.filter.factory.**RequestRateLimiterGatewayFilterFactory**的apply方法，判断是否放行是调用limiter.isAllow方法，limiter是一个**RateLimiter**接口，目前只有一个实现就是**RedisRateLimiter**。  
如果被限流了，这里就会设置http响应码，默认是429 too many request，同时将response设置为complete，响应就会停止传递，这就导致我们无法再对response进行处理，例如重新修改响应码为200。   

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-8.png)   

RedisRateLimiter isAllow方法会获取我们的配置，然后通过一段lua脚本判断是否通过，这段脚本存放在gateway源码script目录下。  
在gateway使用的是非阻塞的ReactiveStringRedisTemplate。   

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-9.png)

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-10.png)

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-11.png)

**带宽问题**   
如果每次执行都传输这段lua脚本，对redis网络带宽会有较大影响，解决这个问题是利用redis server的脚本缓存功能，相关命令是：**eval，evalsha**，[参考](https://redis.io/docs/latest/commands/evalsha/)。  
逻辑如下：首次执行redis server还没有缓存，此时通过sha1去执行server会返回一个NOSCRIPT的错误；判断是该错误后，调用eval命令执行，执行后会计算sha1并缓存脚本，后面则可以直接使用。

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-12.png) 

**弱依赖**       
基于redis实现的限流，那如果redis不可以用了会怎么样？spring cloud的做法值得我们学习，参考。他使用的是**弱依赖**的方式，当redis不可用时，请求会放行，这非常合理，不能因为限流这个辅助功能导致我们正常的请求都无法处理。弱依赖这个思想非常值得借鉴，与弱依赖对应的是强依赖，当依赖方出问题时，流程会进行不下去。     

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-13.png)  

### 动态刷新     
我们希望限流的路由配置是可以动态刷新的，不用每次修改都重启服务，这就需要使用到配置中心，我们项目使用的配置中心是k8s config map，客户端使用的是spring cloud kubernetes。接下来就分析一下它是如何支持路由的动态刷新的，如果你的项目使用的是apollo，nacos，也是大同小异。     

spring cloud kubernetes reload配置如下：    
```
spring.cloud.kubernetes.reload.enabled = true
spring.cloud.kubernetes.reload.strategy = refresh
spring.cloud.kubernetes.reload.mode = event
```

从官方文档可以看到这两个注解的类支持动态刷新，spring cloud gateway的路由是配置在**GatewayProperties**，这个类刚好有@ConfigurationProperties注解。

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-14.png)  

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-15.png)   

GatewayProperties刷新后路由还不会生效，它是被**RouteDefinitionLocator**使用，默认情况下是org.springframework.cloud.gateway.route.**CompositeRouteDefinitionLocator**。   

CompositeRouteDefinitionLocator组合了**PropertiesRouteDefinitionLocator**和**InMemoryRouteDefinitionLocator**，主要是PropertiesRouteDefinitionLocator，它持有一个GatewayProperties对象。  

也就是说，当GatewayProperties刷新了，CompositeRouteDefinitionLocator的route也就刷新了。不过gateway是通过**RouteLocator**是使用路由的，默认是**CachingRouteLocator**，它通过RouteDefinitionLocator获取路由信息后缓存在内部一个ConcurrentHashMap中，所以最终要路由生效需要刷新这个缓存。从类的定义可以看出，它实现了**ApplicationListener**接口，监听**RefreshRouteEvent**事件，在事件发生时会再次调用RouteDefinitionLocator获取路由，更新缓存。  

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-16-2.png)   

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-17.png)  

> RouteDefinitionLocator和RouteLocator是一种存储和计算分离的设计，RouteDefinitionLocator负责存储路由的原始定义，RouteLocator负责将原始路由数据转换为程序使用的对象。   
这样的好处是路由的存储和如何将路由数据转换为程序对象都可以随时切换，外层使用者不会受影响，例如可以将路由定义保存到redis或数据库。 

到这里还没有结束，RefreshRouteEvent事件又是怎么发出来的呢？回到k8s的配置，mode=event，会注入一个**EventBasedConfigMapChangeDetector**，它在启动后就会去监听k8s configmap事件。
当事件触发的时候，根据strategy: refresh，由**ContextRefersher**触发刷新，具体是调用refresh方法，该方法会发出两个事件。    

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-18.png)   

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-19.png)   

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-20.png)   

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-21.png)      

其中**RefreshScopeRefreshedEvent**事件会被**RouteRefreshListener**监听，并发出**RefreshRoutesEvent**事件，至此完成了整个路由刷新。

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-22.png)     

> 从源码可以看出，这里有一点设计缺陷，随便一个标记@ConfigurationProerties或@RefreshScope的类被刷新，路由都会重新加载，如果这个加载是从redis或者数据库获取计算，会产生无畏的开销。  
如果我们只想要针对路由进行处理，可以捕获EnviromentChangeEvent事件，它的key可以知道改的是什么。

**总结**，综上，修改k8s configmap路由配置后可以动态生效，上面整个流程如下图:   

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-23.png)    

# 上线    
## 插曲
开发测试完了，一切都很顺利，但上线前，在预发环境验收时，测试同学反馈限流没有生效...      
接着又是一个“漫长”的排查过程，一直怀疑是哪里配置错了，例如yaml格式配置错误，但反复检查没有问题，也没有错误日志。  
一时之间陷入两难局面，一边找不到思路，一边等着上线，我的做法是先将上线风险和问题汇报给leader和相关人员，先把问题和风险暴露出来，再慢慢来分析、解决问题。      

确认yaml格式等没问题，我将限流参数redis-rate-limiter.burstCapacity设置为0，表示拒绝所有请求，测试发现也没用，表示限流根本不起作用。   
通过**curl -v**，打印请求详细报文，终于发现蛛丝马迹，响应头包含如下几个字段，其中X-RateLimit-Remaining表示剩余令牌数，当时值为-1。    

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-24.png)

接着我们看源码，发现设置-1的，在org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter#isAllowed有两个地方：   

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-25.png)    

最后一处是我们前面说的弱依赖，redis当前肯定是可用的。另一处就是执行后，redis server返回错误，看代码他只打印了debug，而我们程序对于非应用程序包下的日志配置为error，所以如果这里出问题是日志是无法打印的。那就实践验证一下，将这个类的日志打印级别配置为debug，发版后测试，发现果然报错了。   

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-26.png)    

> 这里报错打印debug也有点坑，我认为打印error更加合适，顺手给gateway提了个[PR](https://github.com/spring-cloud/spring-cloud-gateway/pull/3502)，官方已经合并到[4.2.0-M2 release版本](https://github.com/spring-cloud/spring-cloud-gateway/releases/tag/v4.2.0-M2)  
![image](https://github.com/user-attachments/assets/80ad282c-fbfb-4dd8-99bd-ba9f22b5d4f6)

看日志描述是lua脚本的写法，redis集群不支持。这首先很好解释了为什么开发测试环境可以，因为开发测试环境的redis是单节点，而预发生产环境是集群。    
但这不科学啊，spring cloud不可能搞一个redis集群都不支持的功能，这也太水了。我们到spring cloud gateway github搜索一番，发现有人遇到相同问题，并且提出pr修改：https://github.com/spring-cloud/spring-cloud-gateway/pull/2992, 但并没有被采纳，言外之意，spring cloud认为这不是问题。     

难道这跟云厂商有关系？接着我们把报错信息拿到阿里云官方文档搜索一番，发现真相原来如此，阿里云集群代理“多此一举”对lua脚本做了一些额外限制，https://help.aliyun.com/zh/redis/support/usage-of-lua-scripts, 主要就是**script_check_enable**参数，默认是打开的，它要求lua脚本内只能使用keys参数来获取入参，不能定义局部变量，很明显上面的lua脚本是有拿keys来定义局部变量的，所以报错。   

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-27.png)

解决方式其实很简单，我们咨询过阿里云团队，只要客户端能确保keys是在集群内同一个solt的话，这个检查是可以关闭的，没有其它副作用，所以我们将这个参数关闭，问题解决。   

> script_check_enable参数关闭可以立即生效，不用重启redis和客户端服务。   

redis cluster下，lua脚本操作的key必须在同一个节点上，否则就失去原子性。可以使用花括号{}来指定要计算槽位置的哈希标签，相同哈希标签的key会由同一个节点处理，参考：[cluster-keyslot](https://redis.io/docs/latest/commands/cluster-keyslot/)。spring cloud RedisRateLimiter正式利用了这一点，所以我们可以保证lua脚本的key都是在同一个节点上，因此script_check_enable参数可以关闭。          

![image](https://github.com/jmilktea/jtea/blob/master/spring%20cloud/images/gateway-ratelimiter-28.png)      

## 总结    
上线后我们还要对429状态码做监控告警，以便及时发现问题，例如频繁出现告警要考虑是否调整参数，或进行扩容。   
经过这次需求笔者也有一些思考分享一下：  
- 知识储备    
平时要多做好知识储备，以便在用到时更加得心应手，避免出现书到用时方恨少。有时候需求时间紧，可能没有那么多时间给你从0开始学习，这个时候知识储备可以让你更加从容应对。    
- 官方文档   
有问题的时候多查找官方文档，这才是最权威的。像上面的问题，搜索后解决方案跟github那个一样，都是通过修改lua脚本的方式，这明显比我们关闭这个参数要复杂很多。   
- 及时汇报     
问题在我花了一些时间后发现没那么快能解决时，要及时汇报，不要一直埋头去解决问题，而是要让相关人员知晓问题，也可能会提供一些帮助，毕竟旁观者清，别人协助看问题通常能提供更多思路。   
- 思维发散      
思考各种可能，配置问题，版本问题，环境问题，想方设法验证，从可疑性高的开始逐步排查，最终定会水落石出。   
