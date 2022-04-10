事出有一次笔者在使用@Async开启异步功能时，报了循环依赖错误，我们使用的是属性注入的方式，而同样的bean使用@Transactional注解则不会出错，那么为什么@Async就会出错呢？在[这一篇](https://github.com/jmilktea/jtea/blob/master/spring/spring%E5%BE%AA%E7%8E%AF%E4%BE%9D%E8%B5%96.md)我们已经仔细的分析了spring解决循环依赖的原理，最后也留下了这个问题，本篇就来分析@Async出现循环依赖的原因。   

## @Async原理分析   
首先我们得知道@Async的原理，才能更好的理解问题，为什么打上@Async注解就可以异步了？这是spring的核心**动态代理**，spring用这个魔法悄悄的实现了这些功能，那我们就看下它是如何代理的。   

要开启@Async异步功能，首先要打上@EnableAsync注解，那我们就从这个注解开始看。   
@EnableAsync会import一个AsyncConfigurationSelector用于选择需要加载的bean，默认使用的是jdk代理的方式，下面我们也以jdk的方式为例进行分析。需要知道的是，jdk代理的方式需要有接口，如果我们的bean有实现接口，就是使用jdk代理方式，否则会使用cglib代理方式。关于jdk和cglib可以看下[这一篇](https://github.com/jmilktea/jmilktea/blob/master/%E9%9D%A2%E8%AF%95/jdk%E5%8A%A8%E6%80%81%E4%BB%A3%E7%90%86%E4%B8%8Ecglib.md)           
```
@Import(AsyncConfigurationSelector.class)
public @interface EnableAsync {
    AdviceMode mode() default AdviceMode.PROXY;
}
```

AsyncConfigurationSelector指定加载ProxyAsyncConfiguration配置类    
```
public class AsyncConfigurationSelector extends AdviceModeImportSelector<EnableAsync> {
	@Override
	@Nullable
	public String[] selectImports(AdviceMode adviceMode) {
		switch (adviceMode) {
			case PROXY:
				return new String[] {ProxyAsyncConfiguration.class.getName()};
			case ASPECTJ:
				return new String[] {ASYNC_EXECUTION_ASPECT_CONFIGURATION_CLASS_NAME};
			default:
				return null;
		}
	}

}
```

ProxyAsyncConfiguration会加载一个AsyncAnnotationBeanPostProcessor，从名字可以看出它应该实现了BeanPostProcessor接口，并且从[spring bean生命周期](https://github.com/jmilktea/jmilktea/blob/master/spring/spring%20bean%E7%94%9F%E5%91%BD%E5%91%A8%E6%9C%9F.md)经验可以大概猜测，这个BeanPostProcessor在postProcessAfterInitialization会生成代理对象    
```
@Configuration
public class ProxyAsyncConfiguration extends AbstractAsyncConfiguration {

	@Bean(name = TaskManagementConfigUtils.ASYNC_ANNOTATION_PROCESSOR_BEAN_NAME)
	public AsyncAnnotationBeanPostProcessor asyncAdvisor() {
		AsyncAnnotationBeanPostProcessor bpp = new AsyncAnnotationBeanPostProcessor();
		bpp.configure(this.executor, this.exceptionHandler);
		Class<? extends Annotation> customAsyncAnnotation = this.enableAsync.getClass("annotation");
		if (customAsyncAnnotation != AnnotationUtils.getDefaultValue(EnableAsync.class, "annotation")) {
			bpp.setAsyncAnnotationType(customAsyncAnnotation);
		}
		bpp.setProxyTargetClass(this.enableAsync.getBoolean("proxyTargetClass"));
		bpp.setOrder(this.enableAsync.<Integer>getNumber("order"));
		return bpp;
	}

}
```
我们看下AsyncAnnotationBeanPostProcessor的继承体系(idea类名右键选择Diagrams->Show Diagram)       
![image](https://github.com/jmilktea/jtea/blob/master/spring/images/images/async-circular1.png)    

跟踪这个类，会发现它是在抽象基类AbstractAdvisingBeanPostProcessor，实现了postProcessAfterInitialization方法，其中 关键代码如下
```
        	if (isEligible(bean, beanName)) {
			ProxyFactory proxyFactory = prepareProxyFactory(bean, beanName);
			if (!proxyFactory.isProxyTargetClass()) {
				evaluateProxyInterfaces(bean.getClass(), proxyFactory);
			}
			proxyFactory.addAdvisor(this.advisor);
			customizeProxyFactory(proxyFactory);
			return proxyFactory.getProxy(getProxyClassLoader());
		}

		// No proxy needed.
		return bean;
```
在这里会判断是否需要生成代理类，这里有一个关键方法是addAdvisor,会添加一个Advisor。Advisor可以看做spring中的切面，它的主要方法是getAdvice用于获取一个Advice，例如一个MethodInterceptor，用于方法拦截。这里的advisor是什么呢？回到AsyncAnnotationBeanPostProcessor，它同时实现了BeanFactoryAware接口，从spring bean生命周期可以知道BeanFactoryAware.setBeanFacotry要先于postProcessAfterInitialization执行。    
```
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		super.setBeanFactory(beanFactory);
		AsyncAnnotationAdvisor advisor = new AsyncAnnotationAdvisor(this.executor, this.exceptionHandler);
		if (this.asyncAnnotationType != null) {
			advisor.setAsyncAnnotationType(this.asyncAnnotationType);
		}
		advisor.setBeanFactory(beanFactory);
		this.advisor = advisor;
	}
```
可以看到上面的this.advisor实际是一个AsyncAnnotationAdvisor，它的getAdvice方法获取的Advice对象的构建如下   
```
	protected Advice buildAdvice(@Nullable Supplier<Executor> executor, @Nullable Supplier<AsyncUncaughtExceptionHandler> exceptionHandler) {
		AnnotationAsyncExecutionInterceptor interceptor = new AnnotationAsyncExecutionInterceptor(null);
		interceptor.configure(executor, exceptionHandler);
		return interceptor;
	}
```
![image](https://github.com/jmilktea/jtea/blob/master/spring/images/images/async-circular2.png)    
从AnnotationAsyncExecutionInterceptor的继承体系可以看出它就是一个MethodInterceptor，我们可以猜测最终方法的执行就是被这个Interceptor给代理了。        

让我们先回到AsyncAnnotationBeanPostProcessor创建代理对象的位置    
```
return proxyFactory.getProxy(getProxyClassLoader());
```
以jdk代理为例，最终调用的是JdkDynamicAopProxy的方法，它实现了InvocationHandler，所以这里传递的是this对象，最终会执行它的invoke方法。   
```
	@Override
	public Object getProxy(@Nullable ClassLoader classLoader) {
		Class<?>[] proxiedInterfaces = AopProxyUtils.completeProxiedInterfaces(this.advised, true);
		findDefinedEqualsAndHashCodeMethods(proxiedInterfaces);
		return Proxy.newProxyInstance(classLoader, proxiedInterfaces, this);
	}
```
invoke方法比较长，主要是拿到所有的拦截器链chian，也就是通过前面添加进去的Advisor，然后调用getAdvice拿到的Advice对象，然后传递给MethodInvocation执行。        
```
// Get the interception chain for this method.
List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
...
MethodInvocation invocation = new ReflectiveMethodInvocation(proxy, target, method, args, targetClass, chain);
// Proceed to the joinpoint through the interceptor chain.
retVal = invocation.proceed();
```
最终执行的是ReflectiveMethodInvocation.proceed方法，跟进这个方法可以发现它最终执行的是MethodInterceptor.invoke方法，就是上面的AnnotationAsyncExecutionInterceptor   
```
//interceptorsAndDynamicMethodMatchers就是上一步传进来的chain
Object interceptorOrInterceptionAdvice = this.interceptorsAndDynamicMethodMatchers.get(++this.currentInterceptorIndex); 
...
((MethodInterceptor) interceptorOrInterceptionAdvice).invoke(this)
```
所以最终执行的是**AnnotationAsyncExecutionInterceptor.invoke**方法，这个方法就很明显了，它会把我们要执行方法扔给一个AsyncTaskExecutor线程池去执行，这就实现了@Async的异步功能。    
```
public Object invoke(final MethodInvocation invocation) throws Throwable {
        ...
		AsyncTaskExecutor executor = determineAsyncExecutor(userDeclaredMethod);
		Callable<Object> task = () -> {
			try {
				Object result = invocation.proceed();
				if (result instanceof Future) {
					return ((Future<?>) result).get();
				}
			} catch (ExecutionException ex) {...
			}catch (Throwable ex) {...
			}
			return null;
		};

		return doSubmit(task, executor, invocation.getMethod().getReturnType());
	}
```   

## @Async循环依赖分析      
有了前面的基础，结合[循环依赖原理]这一篇，我们几乎可以直接得出结论。下面@Async是如何引起循环依赖错误的，两个循环依赖的类如下：  
```
@Service
public class ASevice {
	@Autowired
	private BSevice bSevice;

	@Async
	public void async(){}
}

@Service
public class BSevice {
	@Autowired
	private ASevice aSevice;
}
```   
启动服务就会报错，报错位置在AbstractAutowireCapableBeanFactory中doCreateBean最后的检查逻辑中，整个过程如下：
- AService进行实例化      
- AService提前暴露自己，**是个AService原始对象**    
- AService进行属性填充，发现需要BService    
- BService进行实例化   
- BService进行属性填充，发现需要AService属性，设置为AService原始对象   
- BService进行初始化，完成   
- AService接着属性填充BService   
- AService进行初始化，**经过AsyncAnnotationBeanPostProcessor后置处理器生成代理对象**    
- 进行检查，发现此时容器中AService是代理对象，与BService依赖的AService不是同一个，默认不允许这种情况发生，报错     

**那么@Transactional注解为什么就不会报错呢？**         
我们也可以直接得出结论，@Transactional提前暴露出去的就是代理对象，接着代理对象被放到二级缓存，后面放到容器中的也是这个代理对象，是同一个，所以不会报错。主要打个断点在此处观察getEarlyBeanReference就可以发现@Async和@Transactional的不同之处，至于@Transactional是由谁怎么生成代理对象的，就不在本次的分析之中，有兴趣的可以继续研究下        
```
addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
```   

**怎么解决呢？**    
1.可以定义一个内部类，把@Async方法放到内部类中，此时AService不会生成代理类，是内部类生成代理类    
```
@Service
public class ASevice {

	@Autowired
	private BSevice bSevice;
	@Autowired
	private AsyncExecutor asyncExecutor;

	//@Async //出现循环依赖
	public void async() {
		asyncExecutor.async();
	}

	@Service
	class AsyncExecutor {
		@Async
		public void async() {
			System.out.println(bSevice.toString());
		}
	}
}
```
2.使用@Lazy懒加载    
在BService注入AService属性打上@Lazy注解，也不会报错了。   
原因是spring在填充属性时，发现打了@Lazy注解就不会马上填充，那自然就不会出现不一致的情况，接着AService会完成初始化，代理对象放入容器中。等到BService使用时才加载AService就是代理对象了。      

此外，也应该尽量在设计上避免循环依赖。还有如果我们把AService重命名为CService，也不会出现错误，因为spring是默认是按照字母顺序加载bean的，如果是CService就是完成后才给代理对象给BService填充属性，也不会有不一致，所以有时候打了@Async没问题，重命名一个类名后就莫名其妙出现循环依赖就是这个原因。    









