# 前言     
[spring cloud sleuth](https://spring.io/projects/spring-cloud-sleuth)是spring cloud全家桶的一员，为spring boot应用程序提供自动配置的分布式日志追踪功能。    
我们通常会使用log.info/warn/error打印日志，在日志比较多或微服务场景下，需要将一个请求的完整日志串联起来分析。例如请求到服务A后，会调用服务B的接口，服务B会发一条消息到mq由服务C消费...，我们在kibana日志中心查看日志时，需要识别各个服务的哪些日志是属于同一个请求，这就是日志追踪要实现的功能。     

**基本概念**    
- traceId：一个链路，一次跟踪。一个请求的开始就会创建一个traceId，后续这个请求的所有调用就会不断的传递下去，通过traceId就可以找到这个请求的完整链路。    
- spanId：一个工作单元。一个链路可以包含多个工作单元，每个工作单元都有自己的生命周期，通过span可以监控到每个工作单元的耗时。   
- MDC：日志上下文。MDC是slf4j包定义的，内部有一个adapter变量，可以导入不同的实现，自动适配。如我们使用logback，就是LogbackMDCAdapter在工作，它内部维护一个ThreadLocal。   
通过MDC设置的变量，最终会在日志打印时使用，使用logback，可以在logback可以在logback.xml使用变量%X{traceId},%X{spanId}使用它。     

spring cloud sleuth在spring boot中开箱即用，对常用的功能组件做了集成。例如feign接口调用，使用spring kafka发送消息，线程池注册到spring容器等都可以打印traceId，spanId。    
定时任务方面，spring scheduling，quartz常用的也做了集成，我们通常使用分布式定时任务调度，例如xxl-job，这个是国产用得比较多的定时任务，sleuth就没有进行集成了，通过xxl-job github查看，也没有对应的集成实现。   

spring cloud sleuth已经支持的模块：   
![image](1)    

# 实现    
在我司，为了解决这个问题有些团队是这样做的，伪代码如下：   
```
@Slf4j
@Component
public class XxxJob extends IJobHandler {

    @Override
    @XxlJob(value = "")
    public ReturnT<String> execute(String param) throws Exception {
        try {
            //添加traceId方便定位日志
            String traceId = String.valueOf(SnowFlakeUtil.getInstance().generateKey());
            MDC.put("traceId", traceId);
            MDC.put("spanId", traceId);        
            return IJobHandler.SUCCESS;
        } catch (Exception e) {
            log.error("error", e);           
            return IJobHandler.FAIL;
        }finally {
            MDC.clear();
        }

    }
}
```    
每次生成一个雪花id作为traceId添加到MDC，定时任务执行完在通过MDC.clear()移除。    
这样做的问题是，这种手动写的方式容易忘记，估计他们是依靠code reivew。另外这种写法也不够优雅，每个定时任务都需要重复写：try→ MDC.put→ finally MDC.clear()。    
另外sleuth本身的traceId,spanId使用的是一个16位的字符（通过一个long类型转换而来），而这种需要自己生成雪花id，在日志中看起来格式也不统一。    
可以将这部分通用逻辑抽取成一个切面，这种做法简单，但缺少一些必要信息，例如类名/方法名，我们可能要使用sleuth进行链路数据上报到zipkin进行查看。      

**他山之石**   
spring cloud sleuth已经对许多常用功能进行集成，我们可以参考他的源码实现，最好的参照物就是scheduling，它也是一个基于注解的定时任务，我们只需要把它换成xxl注解即可。   
![image](2)    

可以看到它定义一个切面，拦截@Scheduled注解，然后在请求执行前后进行设置和清除。    
针对xxl我们可以实现如下，也可以将此作为一个思路去实现提交给xxl-job仓库。    
```
@Aspect
@Order(Integer.MIN_VALUE)
public class XxlJobTraceAspect {

	private final Tracer tracer;

	public XxlJobTraceAspect(Tracer tracer) {
		this.tracer = tracer;
	}

	@Around("execution (@com.xxl.job.core.handler.annotation.XxlJob  * *.*(..))")
	public Object xxlJobTrace(final ProceedingJoinPoint pjp) throws Throwable {
		String spanName = SpanNameUtil.toLowerHyphen(pjp.getSignature().getName());
		AssertingSpan span = XxlJobSpan.XXL_ANNOTATION_SPAN.wrap(startOrContinueSpan()).name(spanName);
		try (Tracer.SpanInScope ws = this.tracer.withSpan(span.start())) {
			span.tag(XxlJobSpan.Tags.CLASS, pjp.getTarget().getClass().getSimpleName())
					.tag(XxlJobSpan.Tags.METHOD, pjp.getSignature().getName());
			return pjp.proceed();
		} catch (Throwable ex) {
			span.error(ex);
			throw ex;
		} finally {
			span.end();
		}
	}

	private Span startOrContinueSpan() {
		Span currentSpan = this.tracer.currentSpan();
		if (currentSpan != null) {
			return currentSpan;
		}
		return XxlJobSpan.XXL_ANNOTATION_SPAN.wrap(this.tracer.nextSpan());
	}

	enum XxlJobSpan implements DocumentedSpan {

		/**
		 * Span that wraps a {@link com.xxl.job.core.handler.annotation.XxlJob} annotated method. Either creates a new span or
		 * continues an existing one.
		 */
		XXL_ANNOTATION_SPAN {
			@Override
			public String getName() {
				return "%s";
			}

			@Override
			public TagKey[] getTagKeys() {
				return Tags.values();
			}

		};

		enum Tags implements TagKey {

			/**
			 * Class name where a method got annotated with @XxlJob.
			 */
			CLASS {
				@Override
				public String getKey() {
					return "class";
				}
			},

			/**
			 * Method name that got annotated with @XxlJob.
			 */
			METHOD {
				@Override
				public String getKey() {
					return "method";
				}
			}
		}
	}
}
```