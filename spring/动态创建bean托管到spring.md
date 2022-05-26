Bean在spring中是一个非常重要的概念，我们平时所写的对象都会作为Bean托管到spring容器，交由spring进行管理，spring为我们提供了Bean生命周期管理，IOC，AOP等核心服务。    
平时我们在类上标记@Controller,@Service，就会生成一个单例的Bean，注册到spring容器，在使用的地方通过@Autowired注入就可以使用它。此外我们平时使用的一些组件，如feign,mybatis等，例如通过@FeignClient或者@Mapper标记一个接口，就可以生成一个代理对象，执行具体的逻辑。**那么这些是怎么做到的呢？**    

本篇我们主要不是进行源码分析，而是通过例子实现一个类似的功能，让我们自己写的注解标记类可以生成Bean托管的spring，通过例子可以大概了解上面@Service,@Mapper的实现原理。     
实现：   
1. @MyService，类似于@Service的功能，标记的类会生成Bean，可以被@Autowired注入使用    
2. @MyMapper，类似于@Mapper的功能，标记的类会生成动态代理对象，执行方法的时候进行拦截       

这两种情况都有一个前提，如何把我们的Bean注册到spring中。    
**ImportBeanDefinitionRegistrar接口**    
spring为我们提供了ImportBeanDefinitionRegistrar这个核心扩展接口，从名字可以看出它是一个BeanDefinition的注册器。在spring中要现有BeanDefinition来对Bean进行描述，例如Bean的名称，类型，是否单例等。这个接口提供一个口子让我们注册BeanDefinition，这样spring就可以生成Bean了。    

## @MyService    
实现这个功能非常简单，只需要简单的几步。   
定义一个MyService注解
```
@Target(value = ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MyService {
}
```
在通过Configuration import一下MyServiceRegistrar，这个就是ImportBeanDefinitionRegistrar接口的实现
```
@Configuration
@Import(value = MyServiceRegistrar.class)
public class MyServiceAutoConfiguration {
}
```
MyServiceRegistrar如下，它通过ClassPathBeanDefinitionScanner去扫描我们知道的包路径，并且只关注有MyService注解的类
```
public class MyServiceRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

	private ResourceLoader resourceLoader;

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		ClassPathBeanDefinitionScanner classPathBeanDefinitionScanner = new ClassPathBeanDefinitionScanner(registry);
		classPathBeanDefinitionScanner.setResourceLoader(resourceLoader);
		classPathBeanDefinitionScanner.addIncludeFilter(new AnnotationTypeFilter(MyService.class));
		classPathBeanDefinitionScanner.scan("com.jmilktea.sample.demo.mybean");
	}
}
```
使用
```
@MyService
public class MyServiceClass {
}

@Service
public class TestClass {

	@Autowired
	private MyServiceClass myServiceClass;
}
```

## @MyMapper   
在使用mybatis我们通过@Mapper就可以生成一个代理对象，帮我们执行sql语句，关于动态代理和mybatis可以参考以前的文章   
[jdk动态代理与cglib](https://github.com/jmilktea/jmilktea/blob/master/%E9%9D%A2%E8%AF%95/jdk%E5%8A%A8%E6%80%81%E4%BB%A3%E7%90%86%E4%B8%8Ecglib.md)    
[springboot是如何集成mybatis的](https://github.com/jmilktea/jmilktea/blob/master/mybatis/springboot%E9%9B%86%E6%88%90mybatis%E5%8E%9F%E7%90%86.md)   

我们自己定义一个MyMapper注解   
```
@Target(value = ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MyMapper {
}
```
同样import一下MyMapperRegistrar
```
@Configuration
@Import(value = MyMapperRegistrar.class)
public class MyMapperAutoConfiguration {
}
```
MyMapperRegistrar也是实现了ImportBeanDefinitionRegistrar接口，这里我们自定义了一个MyMapperScanner
```
public class MyMapperRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

	private ResourceLoader resourceLoader;

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		MyMapperScanner myMapperScanner = new MyMapperScanner(registry);
		myMapperScanner.setResourceLoader(resourceLoader);
		myMapperScanner.addIncludeFilter(new AnnotationTypeFilter(MyMapper.class));
		myMapperScanner.scan("com.jmilktea.sample.demo.mybean.mapper");
	}
}
```
MyMapperScanner代码如下，这里再判断是否是候选bean的时候，如果是，会塞一个属性，就是被标记接口的完全名称的ClassName，并在下一步取出来使用。   
这里我们设置了Bean Class为MyMapperFactoryBean，这是个FactoryBean接口的实现，spring在生成bean判断如果是FactoryBean接口，就会调用它的getObject方法来生成实际的bean，这实际给了一个我们可以拦截bean生成的机会。同时设置了mapperClass属性，这个属性定义在MyMapperFactoryBean，需要有对应的set方法。
```
public class MyMapperScanner extends ClassPathBeanDefinitionScanner {

	public MyMapperScanner(BeanDefinitionRegistry registry) {
		super(registry);
	}

	@Override
	protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
		boolean b = beanDefinition.getMetadata().hasAnnotation(MyMapper.class.getName());
		if (b) {
			beanDefinition.setAttribute("mapperClass", beanDefinition.getMetadata().getClassName());
		}
		return b;
	}

	@Override
	protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry) {
		GenericBeanDefinition beanDefinition = ((GenericBeanDefinition) definitionHolder.getBeanDefinition());
		beanDefinition.setBeanClass(MyMapperFactoryBean.class);
		beanDefinition.getPropertyValues().add("mapperClass", beanDefinition.getAttribute("mapperClass"));
		super.registerBeanDefinition(definitionHolder, registry);
	}
}
```
MyMapperFactoryBean的作用就是通过jdk动态代理来生成代理对象，mapperClass就是上面传递下来的被标记接口的class。   
这里动态代理对象我们见到打印了一行日志，如果是mybatis就可以写数据库交互的逻辑，如果是feign就可以写http交互的逻辑。    
```
public class MyMapperFactoryBean<T> implements FactoryBean<T> {

	private Class<T> mapperClass;
	private JdkProxy jdkProxy = new JdkProxy();

	public void setMapperClass(Class<T> mapperClass) {
		this.mapperClass = mapperClass;
	}

    @Override
	public boolean isSingleton() {
		return true;
	}

	@Override
	public T getObject() throws Exception {
		return jdkProxy.createProxy(mapperClass);
	}

	@Override
	public Class<?> getObjectType() {
		return mapperClass;
	}
}
```
```
public class JdkProxy implements InvocationHandler {

	public <T> T createProxy(Class<T> mapperClass) {
		return (T) Proxy.newProxyInstance(mapperClass.getClassLoader(), new Class[]{mapperClass}, this);
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		System.out.println(method.getName() + " method executing...");
		return null;
	}
}
```
使用
```
@MyMapper
public interface MyMapperInterface {
	void test();
}

@Service
public class TestClass {

	@Autowired
	private MyMapperInterface myMapperInterface;

	public void test() {
        myMapperInterface.test();
    }
}
```

spring提供了许多扩展点方便与之集成，例如本篇提到的ImportBeanDefinitionRegistrar，许多组件都使用它来注入Bean，例如集成mybatis所使用的AutoConfiguredMapperScannerRegistrar，具体可以看下前面的文章。   
平时业务开发我们多数使用现有的Bean，但在中间件开发，组件开发，就会用到这些功能，了解这些扩展点还是很有必要的。     
