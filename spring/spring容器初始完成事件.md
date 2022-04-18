前面我们介绍了[spring bean的生命周期]()，熟悉bean的生命周期我们可以在各个环节做一些自定义逻辑，实现对bean生命周期的管理。   
spring bean生命周期是针对单个bean的，如果我们需要在所有bean初始化完成后，也就是容器初始化结束后做一些操作呢？接下来我们就看如何实现。  

从spring bean生命周期可以看到，BeanPostProcessor是整个容器初始化完成前的一个节点，但是它也是针对单个bean的，每个bean初始化前后都会触发BeanPostProcessor的事件。  
```
@Component
public class ContextBeanPostProcessor implements BeanPostProcessor {

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if(bean.getClass() == SimpleBean.class){
			System.out.println("====ContextBeanPostProcessor " + ((SimpleBean)bean).getName());
		}
		return bean;
	}
}
```
可以看到BeanPostProcessor还是处于单个bean的生命周期中，无法提供整个容器初始完成的事件。  

## SmartInitializingSingleton   
实现这个接口，当所有单例的bean初始化完成时，会执行。如果我们只关注单例的bean，可以近似看成是整个容器都初始化完成了。  
```
@Component
public class ContextSmartInitializingSingleton implements SmartInitializingSingleton {  

	@Autowired
	private SimpleBean simpleBean;

	@Override
	public void afterSingletonsInstantiated() {
		System.out.println("====smart singleton " + simpleBean.getName());
	}
}
```

## ContextRefreshedEvent  
这个就是容器初始化完成的事件，可以实现我们的要求。我们可以通过实现ApplicationListener接口，或者@EventListener注解来捕获这个事件。  
ContextRefreshedEvent是一个ApplicationEvent，它由spring触发，所有的application event都会继承它，ContextRefreshedEvent就是其中一种。  
```
@Component
public class ContextRefreshedEventListener implements ApplicationListener<ContextRefreshedEvent> {

	@Override
	public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
		SimpleBean bean = contextRefreshedEvent.getApplicationContext().getBean(SimpleBean.class);
		System.out.println("====ContextRefreshedEven2 " + bean.getName());
	}

	@EventListener(ContextRefreshedEvent.class)
	public void onApplicationEventWithAnnotation(ContextRefreshedEvent contextRefreshedEvent) {
		SimpleBean bean = contextRefreshedEvent.getApplicationContext().getBean(SimpleBean.class);
		System.out.println("====ContextRefreshedEvent1 " + bean.getName());
	}
}
```

## SmartLifecycle接口  
SmartLiftcycle接口提供了更丰富的方法，可以在容器启动、结束时触发。  
```
 * An extension of the {@link Lifecycle} interface for those objects that require
 * to be started upon {@code ApplicationContext} refresh and/or shutdown in a
 * particular order.
 *
```  
我们从接口的定义可以看到，ApplicationContext的refresh,shutdown都会触发接口的执行。如果有多个SmartLiftcycle接口的实现者，会按照定义的顺序执行。  
其中start方法会在容器触发化完成后触发，stop会在容器消耗时触发。  
```
@Component
public class ContextSmartLifecycle implements SmartLifecycle {

	@Autowired
	private SimpleBean simpleBean;

	private Boolean running;

	@Override
	public void start() {
		running = true;
		System.out.println("====SmartLifecycle start " + simpleBean.getName());
	}

	@Override
	public void stop() {
		System.out.println("====SmartLifecycle stop " + simpleBean.getName());
	}

	@Override
	public boolean isRunning() {
		return running == null ? false : running;
	}

	@Override
	public int getPhase() {
		return 0;
	}
}
```  



