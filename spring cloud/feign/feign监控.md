# 前言     
在实际项目中，使用feign调用外部接口，监控是必不可少的。根据实际经验，外部接口和网络一样是不稳定的，如果没有良好的监控，这里是最容易扯皮的地方。   
当你的接口慢，怀疑是下游接口慢导致，但是下游并不承认，有了监控就可以避免这些不必要的纠纷，同时可以做告警。     
在我的知识体系中，也把feign的监控作为一个重要知识点和使用经验。     

![image](1)    

feign已经自带一了micrometer模块，负责收集feign的相关指标，这个模块非常简单，总共就10几个类，它里面使用的是[micrometer](https://docs.micrometer.io/micrometer/reference/overview.html)这个强大的监控组件。   

![image](2) 

**micrometer提供了一种像slf4j日志一样的门面模式，可以方便的统计程序运行时的各种指标，并适配各种监控系统。**     
这里提到的门面模式，是因为各种监控系统对监控指标的收集，格式，单位有差异，例如prometheus，influx db，jmx等，官网列了有十几种，需要将这些实现的高层抽象化，以便更好的扩展，支持新的监控系统。想想slf4j，日志框架有很多种，有了门面模式在使用时我们并不关心底层使用的是哪种，也可以轻松替换。    
micrometer还有许多功能，例如定义各种指标，Counter用于收集只增不减的数据，如请求数量，异常数量，Gauge用于收集变化的数据，如连接数量，线程数量，Timer用于收集执行时间等，还有像spring过滤器一样的Filter机制，可以在收集过程中拦截处理，全局标签等，更多功能请参见官网。      

门面这个抽象在micrometer就是MeterRegistry抽象类，最常见的使用Prometheus就是PrometheusMeterRegistry。使用springboot actuator时，可以轻松集成prometheus registry。   
```
<dependency>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
	<groupId>io.micrometer</groupId>
	<artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

这样就会注入PrometheusMeterRegistry bean，将指标转换为prometheus支持的规范。    

```
management:
  endpoints:
    web:
      exposure:
        include: prometheus
```
再将promethus端点暴露出来，就可以使用/actuator/promethus访问这些指标，将他们收集到promethus系统中了。     

# 实践    
关于feign的指标收集也非常简单，只需要导入如下包即可：       
```
<dependency>
	<groupId>io.github.openfeign</groupId>
	<artifactId>feign-micrometer</artifactId>
</dependency>
```

接着访问/actuator/promethus就可以得到feign相关的统计数据：   
feign_Client_seconds_count：接口访问次数    
feign_Client_seconds_sum：接口访问总时间     
feign_Client_seconds_max：接口访问最大时间     
feign_Client_http_response_code_total：不同响应码统计    
feign_Client_exception_seconds_count：接口异常次数     
feign_Client_exception_seconds_sum：接口异常时间    
feign_Client_exception_seconds_max：接口异常最大时间     
还有一些关于decoder的指标，这里不做介绍。    

主要注意的是，输出的指标一套feign_Feign_*的，上面是feign_Client，这两个有什么区别的？     
feign_Client是接口调用的统计，feign_Feign是feign方法调用的统计。比如说调用一个unknow host接口，feign_client中只有exception的指标会被记录，feign_Client_seconds_count之类的不会被记录，而feign_Feign的都会被记录，因为它是针对方法的统计，而不管实际有没有这个接口。所以它们输出的指标，feign_Client会有uri这个标签，而feign_Feign则没有。   
另外就算调用正常，这两个指标值也有会细微差别，feign_Client_seconds_sum与feign_Feign_seconds_sum值会不一样，后者会比前者大一点，因为它包含了前者，例如如果feign方法还有过滤器的逻辑，耗时后者也会统计，而前者不会统计。     
通常，我们只需要关注feign_Client_*这套指标即可。     

在我们的实践中，有很多url是动态的，参数和返回值都一样，但url是变化的，这样如果每种写一个feign方法就比较麻烦，可以讲URI作为参数，在外部动态构造好传入，feign就会调用指定的URI。    
```
@FeignClient(name = "myclient")
public interface MyClient {

	@RequestMapping(method = RequestMethod.POST)
	Rsp test(URI uri, @RequestBody Req req);
}
```

很遗憾，这种写法无法监控到每个url，输出的指标的uri tag是：/，如果想精细化监控就不行了。    
不过还是有办法，天无绝人之路。源码之下无秘密，翻看feign-micrometer的源码可以发现，它里面关于这个uri的获取是通过methodMetadata()拿到的，metadata是通过反射feign的方法获取到的的元信息，由于上面的test方法我们没有设置value为指定的url，获取通过metadata就获取不到path，默认就展示了/。
![image](3)    

我的解决方案是继承原来的类，重写相关方法，并在spring注入我们的MeteredClient覆盖原来的bean。主要有如下几个类：     
feign.micrometer.MeteredClient   
feign.micrometer.MeteredDecoder   
com.al.risk.collection.robot.config.MyMicrometerCapability    

注入bean：
```
	@Bean
	public MicrometerCapability micrometerCapability(MeterRegistry meterRegistry) {
		return new MyMicrometerCapability(meterRegistry);
	}
```

MicrometerCapability:
```
public class MyMicrometerCapability extends MicrometerCapability {

	@Override
	public Client enrich(Client client) {
		return new MyMeteredClient(client, meterRegistry);
	}

	@Override
	public Decoder enrich(Decoder decoder) {
		return new MyMeteredDecoder(decoder, meterRegistry);
	}
}
```

需要注意的是，下方获取url都是使用template.path()，而不是request.url()，因为后者会有“标签爆炸”问题。    
这样feign_Client_*指标的tag uri就会显示完整的路径了，带上域名的，如果想显示相对路径，就用URI处理一下即可。   

MeteredClient:
```
public class MyMeteredClient extends MeteredClient {

	@Override
	protected void countResponseCode(Request request,
									 Response response,
									 Options options,
									 int responseStatus,
									 Exception e) {
		final Tag[] extraTags = extraTags(request, response, options, e);
		final RequestTemplate template = request.requestTemplate();
		String uri = template.methodMetadata().template().path();
		final Tags allTags = metricTagResolver
				.tag(template.methodMetadata(), template.feignTarget(), e,
						Tag.of("http_status", String.valueOf(responseStatus)),
						Tag.of("status_group", responseStatus / 100 + "xx"),
						Tag.of("uri", "/".equals(uri) ? template.path() : uri))
				.and(extraTags);
		meterRegistry.counter(
						metricName.name("http_response_code"),
						allTags)
				.increment();
	}

	@Override
	protected Timer createTimer(Request request,
								Response response,
								Options options,
								Exception e) {
		final RequestTemplate template = request.requestTemplate();
		String uri = template.methodMetadata().template().path();
		final Tags allTags = metricTagResolver
				.tag(template.methodMetadata(), template.feignTarget(), e,
						Tag.of("uri", "/".equals(uri) ? template().path() : uri))
				.and(extraTags(request, response, options, e));
		return meterRegistry.timer(metricName.name(e), allTags);
	}
}
```

MeteredDecoder:
```
public class MyMeteredDecoder extends MeteredDecoder {

	protected Counter createExceptionCounter(Response response, Type type, Exception e) {
		final Tag[] extraTags = extraTags(response, type, e);
		final RequestTemplate template = response.request().requestTemplate();
		String uri = template.methodMetadata().template().path();
		final Tags allTags = metricTagResolver.tag(template.methodMetadata(), template.feignTarget(),
						Tag.of("uri", "/".equals(uri) ? template().path() : uri),
						Tag.of("exception_name", e.getClass().getSimpleName()))
				.and(extraTags);
		return meterRegistry.counter(metricName.name("error_count"), allTags);
	}

	protected Tag[] extraTags(Response response, Type type, Exception e) {
		RequestTemplate template = response.request().requestTemplate();
		String uri = template.methodMetadata().template().path();
		return new Tag[]{Tag.of("uri", "/".equals(uri) ? template().path() : uri)};
	}
}
```

    
