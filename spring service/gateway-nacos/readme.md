## 简介  
使用spring cloud技术栈开发，在spring cloud gateway出现以前我们通常使用zuul作为网关服务，zuul 1.x版基于同步servelet，其线程模型为：多线程阻塞模型，适用于计算密集型场景，对于一些io密集型场景，由于线程阻塞容易出现线程耗尽问题。zuul 2.x版本解决了这个问题，使用netty实现异步非阻塞处理请求，不过由于zuul经常跳票，spring cloud团队似乎不再想继续集成zuul，取而代之的自己开发了spring cloud gateway。  
有了网关，就可以将请求转发到对应服务，不过一般不会写死调用服务的地址，而是通过注册中心来实现，网关和服务间的调用不需要关注地址，统一由注册中心维护，这里使用阿里的[nacos](https://nacos.io/zh-cn/docs/concepts.html)来实现。nacos不只可以作为注册中心，还可以作为配置中心，一物两用，减少运维成本。   

## 实现图  
![image](https://github.com/jmilktea/jmilktea/blob/master/spring%20service/gateway-nacos/%E6%95%88%E6%9E%9C%E5%9B%BE.png)

## nacos server  
1. 与eureka类似，我们需要启动一个注册中心server。[下载](https://github.com/alibaba/nacos/releases)nacos稳定版
2. 单节点启动
```
sh startup.sh -m standalone
```
3. 访问 ip:8848/nacos，使用nacos/nacos 账号密码登录，如图可以看到nacos的主要功能是配置中心和注册中心
![image](https://github.com/jmilktea/microservice/blob/master/spring%20service/gateway-nacos/nacos%E5%90%AF%E5%8A%A8.png)

## 接入nacos
创建springboot服务，bootstrap.yml配置为：
```
spring:
  application:
    name: order-service
  cloud:
    nacos:
      discovery:
        server-addr: nacosip:8848
      config:
        server-addr: nacosop:8848
        file-extension: yaml
```
其中discovery.server-addr，config.server-addr分别为注册中心和配置中心地址。user-service类似，启动服务可以看到order-service,user-service出现在服务列表。   
config.file-extension为nacos配置中心dataid的后缀。  
dataid命名规则为：${prefix}-${spring.profile.active}.${file-extension}，prefix默认为spring.application.name，也可以通过spring.cloud.nacos.config.prefix配置。spring.profile.active可以配置不同的配置文件，这里我们没有，所以为空。file-extension为配置内容的格式，支持ymal或者propertie。  
可以看到这里我们没有配置端口和路径等信息，这些都配置到nacos中，程序启动时会自动加载，如order-service:
![image](https://github.com/jmilktea/microservice/blob/master/spring%20service/gateway-nacos/order-service-config2.png)

## 接入gateway  
gateway项目也要接入nacos，才可以从注册中心获取服务地址。  
bootstrap.yml配置为：
```
spring:
  application:
    name: gateway-service
  cloud:
    nacos:
      discovery:
        server-addr: nacosip:8848
      config:
        server-addr: nacosip:8848
        file-extension: yaml
```
gateway配置也是在nacos中，其中路由规则配置为url以/order开头的将转发到order-service如下：
![image](https://github.com/jmilktea/microservice/blob/master/spring%20service/gateway-nacos/gateway-service-config2.png)

## 效果  
返回网关地址：http://localhost:8082/order/config/get 可以看到请求可以转发到order-service，修改config配置的值，可以再次刷新可以看到效果。注意这里需要结合@RefreshScope注解才能动态刷新。  
如上图order-service还通过open feign调用了user-service，使用@FeignClient注解时我们设置name为服务的名称，在eureka中这个过程是透明的，feign会自动解析服务名称，调用服务的地址。同理使用nacos也是一样，服务地址列表一样会通过注册中心推送到客户端，客户端通过名称即可获取到地址。  

## 思考
1.网关高可用
![image](https://github.com/jmilktea/jmilktea/blob/master/spring%20service/gateway-nacos/gateway-ha.png)

