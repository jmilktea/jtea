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
