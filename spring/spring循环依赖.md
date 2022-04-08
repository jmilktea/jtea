循环依赖在实际开发中经常会遇到，也是一个比较常见的面试题目。例如会有如下问法：   
- spring解决了哪些场景下的循环依赖，通过构造函数注入的方式，一定会出现循环依赖吗    
- spring解决循环依赖的原理    
- spring bean三级缓存分别是什么，为什么是三级缓存，不是二级    
- 两个依赖bean都使用属性注入，其中一个bean的方法打上@Async注解，会出现循环依赖报错吗，换成@Transactional呢    

循环依赖如下，更复杂的循环依赖可以不只两个bean         
```
@Service
public class ASevice {
	@Autowired
	private BSevice bSevice;
}

@Service
public class BSevice {
	@Autowired
	private ASevice aSevice;
}
```    

![image](https://github.com/jmilktea/jtea/blob/master/spring/images/images/spring-circular1.png)    

## 原理分析       
先回答这个问题：spring是如何解决循环依赖问题的？     
**spring是通过提前暴露创建中的bean来解决循环依赖的！** 这里的主要思想是：提前暴露自己，让对方拿到一个创建中的bean，以便对方能顺利完成初始化。然后再自己初始化完成，但要保证对方拿到的bean和当前创建的是同一个。           
我们可以将bean的创建简化为如下3个步骤：**实例化->填充属性->初始化**          
1. 实例化。也就是new创建一个对象，此时对象不完整，是个半成品    
2. 填充属性。对bean的属性进行填充赋值   
3. 初始化。执行init方法等，初始化后bean就完整了    

那么提前暴露的bean怎么拿来使用呢？答案是通过缓存，spring设计了三级缓存来缓存bean，至于为什么是三级，后面再回答。三级缓存分别是：     
1级缓存，用于缓存完整的bean，已经完成上面三个步骤    
2级缓存，用于缓存仅实例化的bean或者动态代理对象，此时的bean还未进行属性填充和初始化       
3级缓存，用于缓存bean对象的创建工厂，通过缓存工厂可以延迟创建bean对象    
三级缓存获取bean的逻辑是：先从1级缓存拿，拿到返回；否则从2级缓存拿，拿到则返回；否则从3级缓存拿，拿到就放到2级缓存，并从3级缓存删除，拿不到就返回空。   

三级缓存的代码在**DefaultSingletonBeanRegistry**类中，从名字可以看到这是个单例bean的注册表，从这里可以拿到单例bean信息。   
三级缓存实际就是3个ConcurrentHashMap，如下：   
```
        //一级缓存
	/** Cache of singleton objects: bean name to bean instance. */
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

        //二级缓存
        /** Cache of early singleton objects: bean name to bean instance. */
	private final Map<String, Object> earlySingletonObjects = new HashMap<>(16);

        //三级缓存
	/** Cache of singleton factories: bean name to ObjectFactory. */
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);
```

我们可以通过ApplicationContext.getBean手动获取bean，会走到DefaultSingletonBeanRegistry.getSingleton方法，该方法的逻辑就是上面获取bean的逻辑     
```
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		Object singletonObject = this.singletonObjects.get(beanName);
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			synchronized (this.singletonObjects) {
				singletonObject = this.earlySingletonObjects.get(beanName);
				if (singletonObject == null && allowEarlyReference) {
					ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
					if (singletonFactory != null) {
						singletonObject = singletonFactory.getObject();
						this.earlySingletonObjects.put(beanName, singletonObject);
						this.singletonFactories.remove(beanName);
					}
				}
			}
		}
		return singletonObject;
	}
```

### bean创建源码分析    
接下来我们看下bean是如何一步步创建的，其主要代码在org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#doCreateBean这个方法中，代码删减后关注主要部分：  
```
	protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final @Nullable Object[] args)
			throws BeanCreationException {        
        	// 实例化bean
                // Instantiate the bean. 
		BeanWrapper instanceWrapper = null;
		if (mbd.isSingleton()) {
			instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
		}
		if (instanceWrapper == null) {
			instanceWrapper = createBeanInstance(beanName, mbd, args);
		}
		final Object bean = instanceWrapper.getWrappedInstance();

        	// 解决循环依赖的关键步骤，使用缓存提前暴露bean   
		// Eagerly cache singletons to be able to resolve circular references
		// even when triggered by lifecycle interfaces like BeanFactoryAware.
		boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
				isSingletonCurrentlyInCreation(beanName));
		if (earlySingletonExposure) {             
			addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
		}

        	// Initialize the bean instance.
		Object exposedObject = bean;
		try {
            		//填充属性
			populateBean(beanName, mbd, instanceWrapper);
            		//初始化bean，这里很关键，可能通过BeanPostProcessor改变bean     
			exposedObject = initializeBean(beanName, exposedObject, mbd);
		}
		catch (Throwable ex) {}

        	//检查校验   
		if (earlySingletonExposure) {
			Object earlySingletonReference = getSingleton(beanName, false);
			if (earlySingletonReference != null) {
				if (exposedObject == bean) {
					exposedObject = earlySingletonReference;
				}
				else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
					String[] dependentBeans = getDependentBeans(beanName);
					Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
					for (String dependentBean : dependentBeans) {
						if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
							actualDependentBeans.add(dependentBean);
						}
					}
					if (!actualDependentBeans.isEmpty()) {
                        			//循环依赖异常
						throw new BeanCurrentlyInCreationException();
					}
				}
			}
		}
		return exposedObject;
	}
```

从上面的代码我们可以看到很多核心步骤和关键字了，接下来我们分析下是怎么解决循环依赖的。    
首先实例化AService，并通过如下方法将这**半成品**bean提前暴露在缓存        
```
addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
```
getEarlyBeanReference的主要作用是，判断对象是否需要被代理，如果没有，就返回原始对象，否则返回动态代理对象。    
```
	protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
		Object exposedObject = bean;
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
					SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
					exposedObject = ibp.getEarlyBeanReference(exposedObject, beanName);
				}
			}
		}
		return exposedObject;
	}
```

接着执行AService的填充属性方法，自然会发现需要依赖BService    
此时会发现BService还不存在，也会走到doCreateBean这个方法，创建BService。      
BService实例化后，一样会提前暴露自己，以便还有其它依赖它的Service可以获取到它。   

接着执行BService的填充属性方法，需要拿到A的实例。这里跟的源码比较深了，但是我们可以猜测最终会在缓存中获取A。源码跟踪路径是    
```
org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#populateBean -> 填充属性  
org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor#postProcessProperties -> 执行填充属性  
org.springframework.beans.factory.annotation.InjectionMetadata#inject -> 执行属性注入
org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor.AutowiredFieldElement#inject -> Autowired属性注入 
org.springframework.beans.factory.support.DefaultListableBeanFactory#resolveDependency -> 依赖解析
org.springframework.beans.factory.support.DefaultListableBeanFactory#doResolveDependency -> 依赖解析
org.springframework.beans.factory.config.DependencyDescriptor#resolveCandidate -> 获取bean   
```
resolveCandidate方法会用beanFactory通过beanName去获取bean，看到这里我们已经大概知道了       
```
return beanFactory.getBean(beanName);
```
再往里面跟    
```
org.springframework.beans.factory.support.AbstractBeanFactory#doGetBean   
org.springframework.beans.factory.support.DefaultSingletonBeanRegistry#getSingleton    
``` 
也就是上面我们手动通过ApplicationContext在三级缓存获取bean的逻辑，而AService在上面的步骤已经添加到三级缓存，所以获取到AService实例，并且从三级缓存删除，放到二级缓存。   

BService填充属性后，回到主流程，执行initializeBean，进行初始化，完成后BService已经准备好了，会把BService添加到一级缓存，并从二三级缓存删除    
```
	protected void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.put(beanName, singletonObject);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.add(beanName);
		}
	}
```   
跟踪如下   
![image](https://github.com/jmilktea/jtea/blob/master/spring/images/images/spring-circular2.png)    

BService搞定了，就会重新回到AService的doCreateBean方法，接着走填充属性和初始化流程。       
AService也搞定了，最后会执行一段检查程序，也就是检查一下BService依赖的AService和当前创建好的AService到底是不是同一个，如果不是会抛出异常。    
```
		if (earlySingletonExposure) {
			Object earlySingletonReference = getSingleton(beanName, false);
			if (earlySingletonReference != null) {
				if (exposedObject == bean) {
					exposedObject = earlySingletonReference;
				}
				else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
					String[] dependentBeans = getDependentBeans(beanName);
					Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
					for (String dependentBean : dependentBeans) {
						if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
							actualDependentBeans.add(dependentBean);
						}
					}
					if (!actualDependentBeans.isEmpty()) {
                        			//循环依赖异常
						throw new BeanCurrentlyInCreationException();
					}
				}
			}
		}  
```
AService完成后，也像BService一样，添加到一级缓存，并从二三级缓存删除    

至此核心逻辑已经分析完了，可能出现循环依赖的错误，也就是最后的那个检查可能会抛出BeanCurrentlyInCreationException，那为什么spring要做这个检查呢？不检查不就没事了...      
如果我们的对象都是原始对象，那的确不会有问题。spring的核心是AOP，会包装我们的原始对象，生成动态代理对象！所以我们使用的bean可能是原始对象，也可能是动态代理对象，默认情况下使用的必须是同一个(allowRawInjectionDespiteWrapping可以改变这个行为，下面会说到)，这不能乱。    
所以这里的检查逻辑是：获取earlySingletonReference，最后参数是false，表示不从三级缓存拿了，如果获取到，表示提前暴露的对象在缓存存在，即提前被别人依赖了，可能存在循环依赖，需要进一步检查。再次检查如果exposedObject没有改变，也就是initializeBean没有生成新的代理对象，那就没问题，直接赋值给exposedObject正常退出。否则继续检查依赖的bean，抛出存在循环依赖异常。      

**那么为什么需要三级缓存，两级不可以吗？**    
首先，使用三级缓存的目的是为了解决循环依赖问题，而且是在生成代理对象的情况下的循环依赖。    
假设没有生成动态代理对象，二级缓存已经足够了。它们的创建步骤如下：   
AService实例化，放到二级缓存，填充属性，发现需要BService    
BService实例化，放到二级缓存，填充属性，发现需要AService，从二级缓存拿到AService    
BService完成属性填充和初始化，放到一级缓存  
AService完成属性填充和初始化，放到一级缓存，**关键：此时BService中的AService属性，和我们放到一级缓存的是同一个**       

那如果生成动态代理对象呢，我们还是使用二级缓存。它们的创建步骤如下：   
AService实例化，放到二级缓存，填充属性，发现需要BService    
BService实例化，放到二级缓存，填充属性，发现需要AService，从二级缓存拿到AService    
BService完成属性填充和初始化，放到一级缓存   
AService完成属性填充和初始化，生成代理对象，放到一级缓存，**关键：此时BService中的AService属性是原始对象，我们放到一级缓存的代理对象，不是同一个了**        

这里还可以狡辩一下，提前生成AService的代理对象不可以吗？   
可以是可以，不过这就打乱了bean的生命周期了，所以不好。关于spring bean生命周期可以[参考这里](https://github.com/jmilktea/jtea/blob/master/spring/spring%20bean%E7%94%9F%E5%91%BD%E5%91%A8%E6%9C%9F.md)，代理对象一般在BeanPostProcessor后置处理器生成。    

那如果使用三级缓存，它们的创建步骤如下：  
AService实例化，创建工厂(会创建代理对象)放到三级缓存，填充属性，发现需要BService    
BService实例化，同样放到三级缓存，填充属性，发现需要AService，从三级缓存获取到创建AService的方法，执行拿到A的代理对象，放到二级缓存         
BService完成属性填充和初始化，放到一级缓存   
AService完成属性填充和初始化，从二级缓存拿到代理对象，放到一级缓存，**关键：此时BService中的AService属性是动态代理对象，我们放到一级缓存的也是代理对象，是同一个**       

**循环依赖条件**   
spring并没有，也没有办法解决所有场景下的循环依赖，它只解决了特定情况下的，这是有前提条件的。   
1. 依赖的bean必须是单例   
2. 依赖注入的方式必须不全是构造函数，且bean按bean name字母排序在前的(这是spring加载bean的顺序)，注入方式不能是构造函数       

假设代码如下：   
```
@Service
public class ASevice {
	private BSevice bSevice;

    @Autowired
    public AService(BService bService) {
        this.bService = bService;
    }
}

@Service
public class BSevice {
	private ASevice aSevice;

    @Autowired
    public BService(AService aService) {
        this.aService = aService;
    }
}
```  

第一点很好理解，假设bean不是单例的，那么   
创建AService时，发现需要BSevice，所以创建一个BSevice    
创建BSevice时，发现需要ASevice，且不是单例的，所以前面那个A不能用，所以再创建一个ASevice     
再次创建ASevice时，发现需要BSevice，且不是单例的，所以前面那个B也不能用，所以再创建一个BSevice    
... 无限循环了     

第二点，假设都是构造函数    
实例化AService时，发现需要BService，等待实例化一个BService    
实例化BService时，发现需要AService，等待实例化一个AService    
... 卡死了       

假设AService是属性注入，过程如下：  
实例化AService，放缓存，填充属性，发现需要BService    
实例化BService，发现需要AService，从缓存获取AService，填充属性，初始化，完成   
回到AService，填充属性，初始化，完成     
如果反过来，AService是构造函数注入则不行，这个稍微推敲一下就知道了。    

此外，上面doCreateBean方法中的检查校验，有一个allowRawInjectionDespiteWrapping变量，默认为false，从名字可以看出它表示是否允许注入原始对象，也就是我们本来是要注入代理对象的，能否允许注入原始对象，如果允许那么也不会出现循环依赖，但这会造成混乱，且不是代理对象，有些功能就没有了，一般情况下我们不建议这么做。要配置为true可以如下：   
```
    @Component
    public class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {
        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            ((AbstractAutowireCapableBeanFactory) beanFactory).setAllowRawInjectionDespiteWrapping(true);
        }
    }
```

到这里，我们基本可以回答开头的前3个问题了，对于问题4：   
**两个依赖bean都使用属性注入，其中一个bean的方法打上@Async注解，会出现循环依赖报错吗，换成@Transactional呢**       
实际本篇本来是为了分析这个问题的，因为之前在开发过程中遇到过，但由于需要先分析原理，篇幅已经过长，所以放到下一篇吧。     
对于该问题，我们可以看如下代码，启动后就会报循环依赖的错误，如果把@Async缓存@Transactional则不会。为什么呢？        
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


