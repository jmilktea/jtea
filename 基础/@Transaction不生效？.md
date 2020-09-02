## 前言  
通过我们通过编程式和声明式的方式来使用spring事务，编程式的方式需要我们自己在事务的地方手动编写事务定义、提交和回滚代码，好处是比较灵活，可以细粒度控制；缺点是编码较多，不够优雅。声明式通常是通过@Transactional注解实现，可以标记在类或方法上面。对于@Transactional，一个不生效的典型场景就是同个类内调用有该注解的方法，此时事务不会生效。如我们调用testTransactionCall，事务是不会生效的。接下来我们就来分析这个问题。  
```
@Service
public class TransService {
	@Autowired
	private AccountMapper accountMapper;

	@Transactional(rollbackFor = Exception.class)
	public void testTransaction() {
		accountMapper.insert(1);
		String s = null;
		int l = s.length();
		accountMapper.insert(2);
	}

	public void testTransactionCall() {
		testTransaction();
	}
}
```
## 原理  
spring会通过jdk proxy或cglib proxy来实现动态代理，有了代理就可以在程序执行前后织入逻辑。如上代码，实际spring通过cglib为我们生成了一个代理类，如图：  
![image]()    
可以打印如下信息验证，我们注入的实际是spring通过cglib生成的代理类。另外，由于动态生成的代理类重写了toString方法，所以在idea debug显示的还是我们定义的类。 
```
System.out.println(transService.getClass());  //com.jmilktea.sample.demo.service.TransService$$EnhancerBySpringCGLIB$$6bce284d
System.out.println(transService.getClass().getSuperclass()); //com.jmilktea.sample.demo.service.TransService
```
有了代理就可以在方法执行前进行拦截，对于cglib来说，被代理类方法的调用都会经过MethodInterceptor.intercept，如果方法标记了@Transactional注解，那么intercept就能获取到对应的拦截器TransactionInterceptor，在该方法内会完成事务相关的操作。主要源码如下：
- MethodInterceptor.intercept  
```
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			Object oldProxy = null;
			boolean setProxyContext = false;
			Object target = null;
			TargetSource targetSource = this.advised.getTargetSource();
			try {
				if (this.advised.exposeProxy) {
					oldProxy = AopContext.setCurrentProxy(proxy);
					setProxyContext = true;
				}			
				target = targetSource.getTarget();
				Class<?> targetClass = (target != null ? target.getClass() : null);
				//获取拦截器
				List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
				Object retVal;				
				if (chain.isEmpty() && Modifier.isPublic(method.getModifiers())) {
					//没有拦截器进入这里
					Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
					retVal = methodProxy.invoke(target, argsToUse);
				}
				else {
					//有拦截器进入这里
					retVal = new CglibMethodInvocation(proxy, target, method, args, targetClass, chain, methodProxy).proceed();
				}
				retVal = processReturnType(proxy, target, method, retVal);
				return retVal;
			}
			finally {
				
			}
		}
```
- TransactionInteceptor.invoke
```
	@Override
	@Nullable
	public Object invoke(MethodInvocation invocation) throws Throwable {
		Class<?> targetClass = (invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null);
		//看方法名字就可以推断，在事务范围内调用我们的方法
		return invokeWithinTransaction(invocation.getMethod(), targetClass, invocation::proceed);
	}
```

## 问题分析    
如果上面的原理可以知道，如果我们直接调用的方法没有@Transactional注解，那么就获取不到TransactionInteceptor，在该方法内再次调用有@Transactional注解的方法时，由于此时的调用对象target是我们定义的对象，所以这次调用不会经过代理对象，自然无法被拦截。简单的说，我们在外面注入TransService对象是spring生成的代理对象，此时调用的上下文是代理对象，而在我们方法内调用方法，此时上下文是我们实际定义的对象。如图：


