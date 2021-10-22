## 简介   
sentinel是阿里推出的一个流量控制组件，sentinel是哨兵的意思，它可以守护我们的服务，通过限流、熔断、系统负载控制等方式来保证服务的稳定性，redis中也有哨兵模式，它的作用是守护节点，两者在思想上是类似的。     
在分布式系统中，每个环节出错都可能导致整条链路出问题，最终出现服务雪崩。同时对于突发流量的处理也显得非常重要，服务只能处理100个请求，硬生生给他10000个，最终只会把它打挂，sentinel就是为了解决此类流量控制问题，哨兵可以很少侵入的就集成到我们的服务，之所以说很少是因为我们还需要导包，这种模式很像istio中的sidecar模式。   
相比hystrix,resilience4j，sentinel文档更加齐全，中文文档非常详细，且经过阿里多年的考验，sentinel在功能和性能方面都表现出色。   
[sentinel wiki](https://github.com/alibaba/Sentinel/wiki/%E4%BB%8B%E7%BB%8D)，官方的文档非常详细，这里我们做一些简单的介绍，然后主要关注标题的熔断部分。          

![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/sentinel-circuit-1.png)

## 对比hystrix   
hystrix虽然已经停更，但它在设计上的非常值得借鉴的，resilience4j和sentinel都参考了它的一些设计，并在此基础上做优化。hystrix停更最大的影响不是功能上不再新增，实际上可以满足我们普通的场景，而是出现bug也无法解决，例如熔断，hystrix的熔断bug非常容易触发，出现熔断后不恢复的情况，github上也有很多讨论，但最终都只能另辟蹊径去解决。   
sentinel对比hystrix主要有如下不同：   
- 资源模型和执行模型上   
hystrix通过Command命令对象来封装资源和隔离策略，也就是资源与隔离规则是依赖的。而sentinel的资源定义和规则的分开的，我们可以理解为sentinel为资源定义一个key后，就可以在别处对这个key定义规则，这样的好处是两者不相互依赖，我们程序只需要关注资源，而把复杂和多变的规则抽取出来，可以放到配置文件或者管理平台。   
- 隔离设计    
hystrix支持线程池隔离和信号量隔离，线程池隔离可以为每个资源提供一个单独的执行环境，不同资源不会相互干扰，隔离性强，这也是hystrix默认的隔离方式。线程池隔离的缺点是会开启很多线程，占用系统资源，例如我们系统对接了很多资源，每个都为其分配一个线程池，这样整个系统开启的线程会非常多，同时使用线程池模式资源的执行和主线程的执行是在不同线程上，资源的执行通常都是很快的，这样就会有很多线程切换，影响系统性能。sentinel只提供了信号量隔离的策略，这种策略下资源的执行和主线程是在同个线程，不会有线程切换的开销，如果资源的执行出现问题，通过熔断降级也可以不对其它资源造成影响，所以sentinel放弃了线程池隔离这种模式。（resilience4j也只提供了信号量隔离策略）  
- 熔断降级    
resilience4j和sentinel在熔断上的设计都跟hystirx差不多，例如都提供了open,half-open,close的状态。hystrix的熔断是基于异常比例的，sentinel的熔断策略更加丰富，可以基于异常比例，异常数，慢调用比例。与hystrix类似，在进入half-open后，sentinel也是尝试进行一次调用来判断进入open或者close状态。      

![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/sentinel-circuit-2.png)

## helloworld   
我们先通过官网一个例子来体验一下sentinel的使用   
```
    private static void initFlowRules(){
		List<FlowRule> rules = new ArrayList<>();
		FlowRule rule = new FlowRule();
		rule.setResource("HelloWorld");
		rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
		// Set limit QPS to 20.
		rule.setCount(20);
		rules.add(rule);
		FlowRuleManager.loadRules(rules);
	}
```
代码非常简单明了，定义一个资源，并且设置了这个资源的QPS是20     
```
        int i = 0;
		initFlowRules();
		while (i < 100) {
			i++;
			if(i == 50){
				Thread.sleep(1000);
			}
			Entry entry = null;
			try {
				entry = SphU.entry("HelloWorld");
				/*您的业务逻辑 - 开始*/
				System.out.println("hello world");
				/*您的业务逻辑 - 结束*/
			} catch (BlockException e1) {
				/*流控逻辑处理 - 开始*/
				System.out.println("block!");
				/*流控逻辑处理 - 结束*/
			} finally {
				if (entry != null) {
					entry.exit();
				}
			}
		}
```   
测试代码也非常简单，首先初始化资源。然后SphU.entry()开始进入资源代码块，这段代码会匹配资源的规则。需要注意的是资源是一个逻辑概念，它可以是调用一个外部接口，也可以是一段本地代码。通常我们说限流和熔断都会联想到接口的调用，sentinel主要功能也是在与此，但它也可以用来处理某段代码。    
上述代码在执行20次后就超过了qps 20的限制，开始进入BlockExecetion，等到i==50后过了1s，又可以继续执行，再过20次后又超过qps限制，又进入BlockExeception，符合我们的预期。    

## springboot集成   
接下来我们看如何在springboot中使用sentinel的熔断功能     
sentinel与springboot/cloud集成推荐使用spring-cloud-starter-alibaba-sentinel，同时需要注意springboot/cloud与alibaba-sentinel的[版本对应关系](https://github.com/alibaba/spring-cloud-alibaba/wiki/%E7%89%88%E6%9C%AC%E8%AF%B4%E6%98%8E)。   
这里我们只做本地测试，sentinel的配置没有持久化，生产上需要做更多东西。sentinel本地测试使用还是不够友好，例如控制台的配置没有提供一个简单的持久化功能，服务一重启就没了。另外sentinel支持代码配置规则，也支持控制台配置，但没有像hystrix一样可以在application.ymal配置的方式。    

**控制台**   
控制台的启动非常简单，下载sentinel-dashboard.jar包后直接运行即可   

**配置客户端**   
```
feign.sentinel.enabled=true
spring.cloud.sentinel.transport.dashboard=localhost:8080
spring.cloud.sentinel.transport.port=8719
```
配置客户端，指定控制台的地址，8719端口是在客户端新开的一个端口，用于和dashboard通信。feign.sentinel.enabled=true用于开启对feign的支持。      
需要配置-Dproject.name=service1指定项目名称，这个就是在控制台显示的名称，只能用启动参数配置，如果没有配置，默认使用Application的完全限定名称。   
客户端服务启动后再控制台并不会出现，需要访问一下接口才会注册过去。   

**配置熔断规则**   
为了测试我们配置如下规则   
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/sentinel-circuit-3.png)   

**测试代码**    
```
@FeignClient(name = "service2", url = "localhost:8082")
public interface Service2Client {

	@GetMapping("/test")
	String test(@RequestParam("p") int p);
}

@Service
public class Service2ClientService {

	@Autowired
	private Service2Client service2Client;

	@SentinelResource("feign")
	public void test(Integer p) {
		service2Client.test(p);
	}
}
```
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/sentinel-circuit-4.png)  
@SentinelResource("feign")指定了资源名称，和控制台配置的资源名称需要对应。   
8082这个服务关闭，访问就会报错，那么访问test方法就会按照上面的熔断规则进行。当出现熔断时就不会再调用远端接口，直接抛出DegradeException。过了熔断时长后，又会尝试调用远端接口，再判断是否继续熔断。    
@SentinelResource("feign")这个注解如果直接打在Feign上是无效的，猜测是sentinel没有获取到代理类上的注解，所以没有生效，这个还没找到解决方式。    

上面可以看到规则在客户端重启后就不见了，实际开发过程中我们需要将规则持久化，如持久到到apollo,nacos,zookeeper，这样dashboard与客户端就不直接通信，而转由配置中心将配置规则推送到客户端。   
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/sentinel-circuit-5.png)   
