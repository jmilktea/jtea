## 简介
本篇主要讲的是springboot是如何集成mybatis的，mybatis是一个数据库访问组件，它的主要功能是实现数据库数据源管理，sql语句执行，事务管理等，它不依赖于spring，也就是说mybatis完全可以用于其它开发框架。mybatis的功能非常多，我们这里只关注springboot如何把它集成进来，不关注里面的实现细节，本篇实际关注以下问题，也是面试经常会问到的。   
1.Mapper是如何变成bean注入到spring容器的   
2.Mapper是如何变成代理对象的   
3.Mapper是如何执行sql语句的  

Mapper注解是mybatis提供的，但它不像@Service @Bean这些是spring可以识别的，也就是打了Mapper后，在某个时刻，会把它变成一个bean，然后注入到spring容器，由spring管理，这样我们代码才可以使用@Autowired来实现注入。还有一个MapperScan注解，可以扫描指定的包名，不用每个接口都打上Mapper注解，这个是spring提供的，两者本质原理一样。  
另外Mapper本身是一个接口，怎么执行sql语句呢，这里mybatis用到的是动态代理来生成一个实际的对象，动态代理对于理解源码非常重要，可以先参考[这里]()。  
接下来我们就通过源码来解答上面的问题。  


## 源码分析  
我们带着上面两个问题开始，源码我们只看关键的代码，忽略不是本次关注的。在springboot中使用mybatis只需要导入starter包  
```
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>2.1.2</version>
        </dependency>
```
导入后的包如下  
![image](https://github.com/jmilktea/jmilktea/blob/master/mybatis/images/spring-mybatis1.png)   
其中mybatis包就是mybatis的核心代码，与spring无关。mybatis-spring包是spring集成mybatis的关键，就是这个包把mybatis融入到spring的各个环节，由spring接管。mybatis-spring-boot-autoconfigure是springboot中常见的模式，用于自动配置，导入一些配置类。  
![image](https://github.com/jmilktea/jmilktea/blob/master/mybatis/images/spring-mybatis2.png)  

## MybatisAutoConfiguration   
一些从MybatisAutoConfiguration开始！它主要做几个事情，注册SqlSessionFactory bean，注册SqlSessionTemplate bean，开始扫描Mapper。  
```
@org.springframework.context.annotation.Configuration
@ConditionalOnClass({ SqlSessionFactory.class, SqlSessionFactoryBean.class })
@ConditionalOnSingleCandidate(DataSource.class)
@EnableConfigurationProperties(MybatisProperties.class)
@AutoConfigureAfter({ DataSourceAutoConfiguration.class, MybatisLanguageDriverAutoConfiguration.class })
public class MybatisAutoConfiguration implements InitializingBean {


  @Bean
  @ConditionalOnMissingBean
  public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
    SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
    //...
    return factory.getObject();
  }

  @Bean
  @ConditionalOnMissingBean
  public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
    ExecutorType executorType = this.properties.getExecutorType();
    if (executorType != null) {
      return new SqlSessionTemplate(sqlSessionFactory, executorType);
    } else {
      return new SqlSessionTemplate(sqlSessionFactory);
    }
  }

  public static class AutoConfiguredMapperScannerRegistrar implements BeanFactoryAware, ImportBeanDefinitionRegistrar {

    private BeanFactory beanFactory;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
      //...
      BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(MapperScannerConfigurer.class);
      builder.addPropertyValue("annotationClass", Mapper.class);          
      registry.registerBeanDefinition(MapperScannerConfigurer.class.getName(), builder.getBeanDefinition());
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
    }
  }

  @org.springframework.context.annotation.Configuration
  @Import(AutoConfiguredMapperScannerRegistrar.class)
  @ConditionalOnMissingBean({ MapperFactoryBean.class, MapperScannerConfigurer.class })
  public static class MapperScannerRegistrarNotFoundConfiguration implements InitializingBean {

  }
}
```

SqlSessionFactory是mybatis的一个工厂类，用于根据配置创建SqlSession。   
SqlSessionTemplate是一个SqlSession，SqlSession也是mybatis中的核心对象，在mybatis表示sql会话，复杂与数据库交互。SqlSessionTemplate是mybatis-spring提供的，是线程安全、由spring管理的对象，既然是spring提供的，那么它并不真正表示会话。SqlSession还有一个重要的实现类DefaultSqlSession，这个是mybatis提供的，真正表示sql会话，它与SqlSessionTemplate的关系会在下面代码中提现。  
AutoConfiguredMapperScannerRegistrar实现了ImportBeanDefinitionRegistrar接口，可以动态注册BeanDefinition，这里注册了MapperScannerConfigurer。这里是Mapper第一次出现的地方，把它赋值给MapperScannerConfigurer的annotationClass属性，后面会用到。  

## MapperScannerConfigurer   
从名称可以看出它是一个MapperScanner，mapper扫描器，用于发现mapper接口。它实现了BeanDefinitionRegistryPostProcessor，也就是在BeanDefinitionRegistry后，开始扫描。  
```
public class MapperScannerConfigurer
    implements BeanDefinitionRegistryPostProcessor, InitializingBean, ApplicationContextAware, BeanNameAware {
        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        if (this.processPropertyPlaceHolders) {
        processPropertyPlaceHolders();
        }

        ClassPathMapperScanner scanner = new ClassPathMapperScanner(registry);
        scanner.setAddToConfig(this.addToConfig);
        scanner.setAnnotationClass(this.annotationClass); 
        scanner.setMarkerInterface(this.markerInterface);
        scanner.setSqlSessionFactory(this.sqlSessionFactory);
        scanner.setSqlSessionTemplate(this.sqlSessionTemplate);
        scanner.setSqlSessionFactoryBeanName(this.sqlSessionFactoryBeanName);
        scanner.setSqlSessionTemplateBeanName(this.sqlSessionTemplateBeanName);
        scanner.setResourceLoader(this.applicationContext);
        scanner.setBeanNameGenerator(this.nameGenerator);
        scanner.setMapperFactoryBeanClass(this.mapperFactoryBeanClass);
        if (StringUtils.hasText(lazyInitialization)) {
        scanner.setLazyInitialization(Boolean.valueOf(lazyInitialization));
        }
        scanner.registerFilters();
        scanner.scan(
            StringUtils.tokenizeToStringArray(this.basePackage, ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS));
    }
    }
```
## ClassPathMapperScanner  
通过名称可以看出，它是一个根据class path路径扫描的对象，默认是扫描路径是我们的Application的包路径，这里是核心逻辑所在。    
scan方法是基类ClassPathBeanDefinitionScanner提供的，这个是spring的方法，最终会调用doScan方法，这是一个Protected方法，会被子类重写。  
```
	public int scan(String... basePackages) {
		int beanCountAtScanStart = this.registry.getBeanDefinitionCount();		
        doScan(basePackages); //扫描basePackages  
		if (this.includeAnnotationConfig) {
			AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
		}
		return (this.registry.getBeanDefinitionCount() - beanCountAtScanStart);
	}
``` 
ClassPathMapperScanner.doScan还是调用了父类的doScan方法，如果不为空就做一些处理。  
```
  public Set<BeanDefinitionHolder> doScan(String... basePackages) {
    Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);

    if (beanDefinitions.isEmpty()) {
      LOGGER.warn(() -> "No MyBatis mapper was found in '" + Arrays.toString(basePackages)
          + "' package. Please check your configuration.");
    } else {
      processBeanDefinitions(beanDefinitions);
    }

    return beanDefinitions;
  }    
```
可以看到这里最终的结果是找到BeanDefinitionHolder，这个可以看成BeanDefinition的一个包装即可。  
```
    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
		Set<BeanDefinitionHolder> beanDefinitions = new LinkedHashSet<>();
		for (String basePackage : basePackages) {
			Set<BeanDefinition> candidates = findCandidateComponents(basePackage);
			for (BeanDefinition candidate : candidates) {
				ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(candidate);
				candidate.setScope(scopeMetadata.getScopeName());
				String beanName = this.beanNameGenerator.generateBeanName(candidate, this.registry);
                //...
				if (checkCandidate(beanName, candidate)) {
					BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(candidate, beanName);
					definitionHolder =
							AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
					beanDefinitions.add(definitionHolder);
					registerBeanDefinition(definitionHolder, this.registry);
				}
			}
		}
		return beanDefinitions;
	}
```
findCandidateComponents是在候选的bean，最终还会checkCandidate检查一下，通过则添加到this.registry，也就是BeanDefinitionRegistry，这个是上一步构造函数传递进来
的。  
findCandidateComponents逻辑如下：  
```
	public Set<BeanDefinition> findCandidateComponents(String basePackage) {
		if (this.componentsIndex != null && indexSupportsIncludeFilters()) {
			return addCandidateComponentsFromIndex(this.componentsIndex, basePackage);
		}
		else {
			return scanCandidateComponents(basePackage);
		}
	}    
```
```
    private Set<BeanDefinition> scanCandidateComponents(String basePackage) {
		Set<BeanDefinition> candidates = new LinkedHashSet<>();
		try {
			String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
					resolveBasePackage(basePackage) + '/' + this.resourcePattern;
			Resource[] resources = getResourcePatternResolver().getResources(packageSearchPath);						
			for (Resource resource : resources) {
				if (resource.isReadable()) {
					try {
						MetadataReader metadataReader = getMetadataReaderFactory().getMetadataReader(resource);
						if (isCandidateComponent(metadataReader)) {
							ScannedGenericBeanDefinition sbd = new ScannedGenericBeanDefinition(metadataReader);
							sbd.setSource(resource);
							if (isCandidateComponent(sbd)) {							
								candidates.add(sbd);
							}
						}
					}
					catch (Throwable ex) {
					}
				}
			}
		}
		catch (IOException ex) {		
		}
		return candidates;
	}
    protected boolean isCandidateComponent(MetadataReader metadataReader) throws IOException {
		for (TypeFilter tf : this.excludeFilters) {
			if (tf.match(metadataReader, getMetadataReaderFactory())) {
				return false;
			}
		}
		for (TypeFilter tf : this.includeFilters) {
			if (tf.match(metadataReader, getMetadataReaderFactory())) {
				return isConditionMatch(metadataReader);
			}
		}
		return false;
	}
```
这里是根据包的路径找到所有.class结尾的文件，this.resourcePattern=**/*.class。  
找到这些文件后，isCandidateComponent判断是否是候选者，是才是我们要找的目标。怎么判断呢？   
每种组件的判断逻辑可能不一样，例如mybatis是通过Mapper，其它组件可能是通过别的注解等，上面我们说到doScan是spring的方法，spring当然要兼容各种场景，所以这里设计了Filter，通过过滤器来判断是否符合要求，excludeFilters/includeFilters就是判断排除或者包含。  
那么mybatis-spring是怎么做的呢？   
这里回到上面MapperScannerConfigurer，有一个registerFilters方法，就是在这里实现判断，代码如下：
```
public void registerFilters() {
    //...
    if (this.annotationClass != null) {
      addIncludeFilter(new AnnotationTypeFilter(this.annotationClass));    
    }
  }
```
annotationClass属性是在MybatisAutoConfiguration就设置为Mapper。所以这里的意思就是包含了Mapper注解就是满足条件。  
至此，标记了Mapper注解的接口已经生成了BeanDefinition，也就是bean的元信息，元信息是用于生成bean的，它本身还不是一个bean。   
上面我们说到doScan如果扫描结果不为空，就会做一些处理，如下：
```
private void processBeanDefinitions(Set<BeanDefinitionHolder> beanDefinitions) {
    GenericBeanDefinition definition;
    for (BeanDefinitionHolder holder : beanDefinitions) {
      definition = (GenericBeanDefinition) holder.getBeanDefinition();
      String beanClassName = definition.getBeanClassName();

      definition.getConstructorArgumentValues().addGenericArgumentValue(beanClassName); // issue #59
      definition.setBeanClass(this.mapperFactoryBeanClass);

      definition.getPropertyValues().add("addToConfig", this.addToConfig);

      boolean explicitFactoryUsed = false;
      if (StringUtils.hasText(this.sqlSessionFactoryBeanName)) {
        definition.getPropertyValues().add("sqlSessionFactory",
            new RuntimeBeanReference(this.sqlSessionFactoryBeanName));
        explicitFactoryUsed = true;
      } else if (this.sqlSessionFactory != null) {
        definition.getPropertyValues().add("sqlSessionFactory", this.sqlSessionFactory);
        explicitFactoryUsed = true;
      }
      if (StringUtils.hasText(this.sqlSessionTemplateBeanName)) {
        definition.getPropertyValues().add("sqlSessionTemplate",
            new RuntimeBeanReference(this.sqlSessionTemplateBeanName));
        explicitFactoryUsed = true;
      } else if (this.sqlSessionTemplate != null) {
        definition.getPropertyValues().add("sqlSessionTemplate", this.sqlSessionTemplate);
        explicitFactoryUsed = true;
      }
      if (!explicitFactoryUsed) {        
        definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
      }
      definition.setLazyInit(lazyInitialization);
    }
  }
```
其中最核心的一句就是：definition.setBeanClass(this.mapperFactoryBeanClass); 为BeanDefinition设置beanClass，
this.mapperFactoryBeanClass=MapperFactoryBean.class。这个有什么用呢？   

## MapperFactoryBean  
当我们设置了BeanDefinition.beanClass后，在创建bean时，就会用我们设置的对象来创建，上面设置的是一个MapperFactoryBean。
MapperFactoryBean实现了FactoryBean接口，定义如下，其中getObject就是用来生成实际bean对象的。   
```
public interface FactoryBean<T> {

	@Nullable
	T getObject() throws Exception;

	@Nullable
	Class<?> getObjectType();

	default boolean isSingleton() {
		return true;
	}
}
```
MapperFactoryBean重写了getObject方法，如下：
```
  @Override
  public T getObject() throws Exception {
    return getSqlSession().getMapper(this.mapperInterface);
  }
```
可以看到这里是获取真正的bean。getSqlSession是获取SqlSession，拿到的是一个SqlSessionTemplate。  

## 生成代理对象
getMapper方法如下：
```
  @Override
  public <T> T getMapper(Class<T> type) {
    return getConfiguration().getMapper(type, this);
  }
```
继续往下跟，最终调用了MapperProxyFactory.newInstance方法
```
public class MapperProxyFactory<T> {

  //...

  @SuppressWarnings("unchecked")
  protected T newInstance(MapperProxy<T> mapperProxy) {
    return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[] { mapperInterface }, mapperProxy);
  }

  public T newInstance(SqlSession sqlSession) {
    final MapperProxy<T> mapperProxy = new MapperProxy<>(sqlSession, mapperInterface, methodCache);
    return newInstance(mapperProxy);
  }

}
```
到这里我们终于看到jdk动态代理的身影了...，jdk动态代理需要一个InvocationHandler对象，这里传递的是MapperProx，到这里对象的注入就完成了。  
我们debug可以看到Mapper确实是一个MapperProxy对象。  
![image](https://github.com/jmilktea/jmilktea/blob/master/mybatis/images/spring-mybatis3.png)   

## MapperProxy执行  
从上面的分析可以看出，我们执行Mapper的方法时，会执行MapperProxy的invoke方法   
```
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, args);
      } else {
        return cachedInvoker(method).invoke(proxy, method, args, sqlSession);
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
  }
```
cacheInvoker方法会将要执行的方法和MapperMethodInvoker缓存一个Map，实际这里是PlainMethodInvoker   
```
  private MapperMethodInvoker cachedInvoker(Method method) throws Throwable {
    try {
      return methodCache.computeIfAbsent(method, m -> {
          //...
          return new PlainMethodInvoker(new MapperMethod(mapperInterface, method, sqlSession.getConfiguration()));
      });
    } catch (RuntimeException re) {
    }
  }

  private static class PlainMethodInvoker implements MapperMethodInvoker {
    private final MapperMethod mapperMethod;

    public PlainMethodInvoker(MapperMethod mapperMethod) {
      super();
      this.mapperMethod = mapperMethod;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable {
      return mapperMethod.execute(sqlSession, args);
    }
  }
```
MapperMethod是Mapper Method方法的一个包装，最终执行的是它的execute方法。如下，这里已经有点sql的味道了，会判断执行语句的类型，调用具体的方法    
```
public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    switch (command.getType()) {
      case INSERT: {
      }
      case UPDATE: {
      }
      case DELETE: {
      }
      case SELECT:
        Object param = method.convertArgsToSqlCommandParam(args);
        result = sqlSession.selectOne(command.getName(), param);
        break;
      case FLUSH:
      default:
    }
    return result;
  }
```
可以看到最终执行的还是SqlSession的方法，这里的SqlSession就是前面的SqlSessionTemplate   
以selectOne为例，我们回过头看看SqlSessionTemplate   
```
  @Override
  public <T> T selectOne(String statement, Object parameter) {
    return this.sqlSessionProxy.selectOne(statement, parameter);
  }
```
这里还有一个proxy...它是通过sqlSessionProxy代理对象执行的，也是动态代理，这个对象是我们在new的时候就已经创建    
```
  public SqlSessionTemplate(SqlSessionFactory sqlSessionFactory, ExecutorType executorType,
      PersistenceExceptionTranslator exceptionTranslator) {
    this.sqlSessionFactory = sqlSessionFactory;
    this.executorType = executorType;
    this.exceptionTranslator = exceptionTranslator;
    this.sqlSessionProxy = (SqlSession) newProxyInstance(SqlSessionFactory.class.getClassLoader(),
        new Class[] { SqlSession.class }, new SqlSessionInterceptor());
  }
```
所以最终执行的是SqlSessionInterceptor这个InvocationHandler的invoke方法，如下  
```
private class SqlSessionInterceptor implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      SqlSession sqlSession = getSqlSession(SqlSessionTemplate.this.sqlSessionFactory,
          SqlSessionTemplate.this.executorType, SqlSessionTemplate.this.exceptionTranslator);
      try {
        Object result = method.invoke(sqlSession, args);
        if (!isSqlSessionTransactional(sqlSession, SqlSessionTemplate.this.sqlSessionFactory)) {
          sqlSession.commit(true);
        }
        return result;
      } catch (Throwable t) {
      } finally {
        if (sqlSession != null) {
          closeSqlSession(sqlSession, SqlSessionTemplate.this.sqlSessionFactory);
        }
      }
    }
  }
```
这里会获取真正的SqlSession，获取到的是mybatis提供的DefaultSqlSession对象，最终执行的就是DefaultSqlSession的selectOne方法。   

到这里“魔法”就完成了，最终执行的还是mybatis的核心方法。这里主要涉及到知识点是spring bean的管理和jdk动态代理，spring通过这两个技术把mybatis平滑的集成到spring应用中，让我们可以轻松使用。

