## 前言
网关对于微服务来说是必不可少的，它相当于一个“门面”和"切面"。门面即把服务和外部隔离，外部只能跟网关进行交互，不需要关注里面的服务。切面即可以把网关当成一个系统级别的拦截器，关注的是横切关注点，如鉴权，限流，日志等。本章介绍的是spring cloud gateway的过滤器。

## spring cloud gateway   
请求过程如下图

客户端向Spring Cloud Gateway发出请求，如果Gateway Handler Mapping确定请求与路由匹配，则将其发送到Gateway Web Handler。此handler通过特定于该请求的过滤器链处理请求。
从功能上来说，filter可以分为全局和特定两种，全局即对所有请求生效，特定可以针对某些服务生效。全局的过滤器继承了GlobalFilter，特定过滤器可以继承GatewayFilter或者AbstractGatewayFilterFactory。

## 实现
- GlobalFilter  
其中order越低表示优先级越高
```
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static ObjectMapper objectMapper = new ObjectMapper();

    /**
     * ServerWebExchange 包装了Http Request和Response
     *
     * @param exchange
     * @param chain
     * @return
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (path.startsWith("/public")) {
            return chain.filter(exchange);
        }
        //假设参数需要token
        String token = request.getQueryParams().getFirst("token");
        if (token == null) {
            DataBuffer dataBuffer = null;
            ServerHttpResponse response = exchange.getResponse();
            Result result = new Result(false, "token为空，鉴权失败");
            try {
                dataBuffer = response.bufferFactory().wrap(objectMapper.writeValueAsBytes(result));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
            return response.writeWith(Mono.just(dataBuffer));
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
```
- GatewayFilter  
这里使用的是AbstractGatewayFilterFactory，这样可以在配置文件进行配置，直接配置UserLog名称即可，不需要"GatewayFilterFactory"，这里是“约定大于配置”的思想。如果使用的是继承GatewayFilter，则不能在配置文件配置，需要用java代码的形式进行配置。

```
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://localhost:8083
          predicates:
            - Path=/user/**
          filters:
            - UserLog
```
```
/**
 * @author huangyb
 * @date 2019/12/5
 */
@Component
public class UserLogGatewayFilterFactory extends AbstractGatewayFilterFactory {

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            System.out.println("user gate way log");
            return chain.filter(exchange);
        };
    }
}
```