- 代理模式  
- jdk动态代理、cglib实现原理，效率    
- jdk动态代理有哪些限制，cglib有哪些限制   
- spring默认使用哪种方式，springboot默认使用哪种方式，通过什么参数配置    
- 除了jdk动态代理和cglib之外，还有哪些方式可以实现动态代理    

## 代理模式    
代理模式的意图是通过一个类代替真实类，实现对真实类的控制，是一种结构型模式。如图：  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E9%9D%A2%E8%AF%95/images/proxy.png)   

RequestHandlerProxy代理了SimpleRequestHandler实现了对它的控制，可以增加打印请求日志的功能。  
与代理模式很像的是装饰器模式，也是一种结构型模式。两者的区别是装饰器重点在于装饰，在于增强功能，而代理模式重点在于控制行为，它不会像装饰器模式一样对外添加新的功能。  
java io流就是一个典型的装饰器模式的实现，通过不同的装饰器给流添加不同的功能，如BufferedInputStream能为InputStream添加缓冲的功能，它会把一些数据加载到buffer，提升性能，这个是普通的InputStream所不具备的。   

代理模式又分发静态代理和动态代理，静态代理很好理解就是在编译时就确定的，性能好，但是缺乏灵活性。动态代理就是我们本次关注的，java里实现动态代理常用的有jdk动态代理和cglib。  

## jdk动态代理和cglib  
我们先通过代码示例看下两者的使用方式：  
jdk动态代理主要使用InvocationHandler接口和Proxy类来实现，InvocationHandler定义了invoke方法，在真实对象方法调用时会调用该方法，再通过反射调用原始方法，Proxy用来创建代理对象。    
```
    interface RequestHandler {
		void show();
	}

	class SimpleRequestHandler implements RequestHandler {
		@Override
		public void show() {
			System.out.println("simple request handler");
		}
	}

	class InvocationRequestHandler implements InvocationHandler {

		private Object target;

		public InvocationRequestHandler(Object target) {
			this.target = target;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			System.out.println("log before request");
			Object r = method.invoke(target, args);
			System.out.println("log after request");
			return r;
		}
	}

	class RequestHandlerProxyFactory {
		public Object createProxy(Object target) {
			InvocationRequestHandler invocationRequestHandler = new InvocationRequestHandler(target);
			return Proxy.newProxyInstance(target.getClass().getClassLoader(), target.getClass().getInterfaces(), invocationRequestHandler);
		}
	}

	@Test
	public void test() {
		RequestHandlerProxyFactory proxyFactory = new RequestHandlerProxyFactory();
		RequestHandler proxy = (RequestHandler) proxyFactory.createProxy(new SimpleRequestHandler());
		proxy.show();
	}
```
需要注意的是网上几乎所有的示例代码都将这里的InvocationRequestHandler命名为Proxy，这是不合理的，它不是一个真正的Proxy，而是作为参数传递给Proxy，由Proxy去触发。我们上面的命名是准确的，没有Proxy类，因为它是动态生成的，我们通过ProxyFactory去生成它。  
Proxy.newProxyInstance第二个参数传递的是interface，也就是target所实现的接口，所以这种方式下我们的类是需要有实现接口的，如果把上面RequestHandler接口相关定义去掉，那么运行起来就会报错，提示
```
java.lang.ClassCastException: com.sun.proxy.$Proxy20 cannot be cast to SimpleTests$SimpleRequestHandler    
```

为了眼见为实，我们可以设置-Dsun.misc.ProxyGenerator.saveGeneratedFiles=true把生成的代理类的class文件保存下来，这个类才是真正的Proxy。如下可以看到生成的代理类也实现了RequestHandler接口，在调用show方法时，实际是调用了super.h的invoke方法，这个h就是构造方法传递进来的InvocationHandler，最终指向的就是我们上面的InvocationRequestHandler的invoke方法。  

```
final class $Proxy20 extends Proxy implements RequestHandler {
    private static Method m1;
    private static Method m3;
    private static Method m2;
    private static Method m0;

    public $Proxy20(InvocationHandler var1) throws  {
        super(var1);
    }

    public final void show() throws  {
        try {
            super.h.invoke(this, m3, (Object[])null);
        } catch (RuntimeException | Error var2) {
            throw var2;
        } catch (Throwable var3) {
            throw new UndeclaredThrowableException(var3);
        }
    }

    static {
        try {
            m1 = Class.forName("java.lang.Object").getMethod("equals", Class.forName("java.lang.Object"));
            m3 = Class.forName("SimpleTests$RequestHandler").getMethod("show");
            m2 = Class.forName("java.lang.Object").getMethod("toString");
            m0 = Class.forName("java.lang.Object").getMethod("hashCode");
        } catch (NoSuchMethodException var2) {
            throw new NoSuchMethodError(var2.getMessage());
        } catch (ClassNotFoundException var3) {
            throw new NoClassDefFoundError(var3.getMessage());
        }
    }
}
```

[cglib](https://github.com/cglib/cglib)的全称是Code Generation Library，是基于asm字节码技术，用于生成和转换java字节码高级api类库，通常用于AOP，测试，数据访问框架等生成动态代理对象和拦截属性的访问。  
还是上面的接口和类，我们看下cglib是示例   
同样的网上很多示例代码都将类命名为Proxy，也是不合理的，这里只是一个Interceptor。  
```
    class CglibInterceptor implements MethodInterceptor {

		private Object target;

		public CglibInterceptor(Object target) {
			this.target = target;
		}

		@Override
		public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
			System.out.println("log before request");
			Object r = method.invoke(target, objects);
			System.out.println("log after request");
			return r;
		}
	}

	class CglibProxyFactory {

		public Object createProxy(Object target) {
			Enhancer enhancer = new Enhancer();
			//指定动态代理类的父类
			enhancer.setSuperclass(target.getClass());
			//设置代理类的拦截器
			enhancer.setCallback(new CglibInterceptor(target));
			//创建并返回代理对象
			return enhancer.create();
		}
	}

    @Test
	public void testCglibProxy() {
		CglibProxyFactory cglibProxyFactory = new CglibProxyFactory();
		SimpleRequestHandler simpleRequestHandler = (SimpleRequestHandler) cglibProxyFactory.createProxy(new SimpleRequestHandler());
		simpleRequestHandler.show();
	}
```

如果SimpleRequestHandler不实现RequestHandler接口，这里还是能正常运行，这个是cglib与jdk动态代理一个很重要的区别，jdk要求必须实现接口，而cglib通过继承的方式生成被代理被的子类，并不需要管有没有实现接口。  
设置System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "C:\\class");可以把cglib生成的.class类保存下来，然后拖拽到idea可以看到继承了我们的类  
```
public class SimpleRequestHandler$$EnhancerByCGLIB$$e18bacb9 extends SimpleRequestHandler implements Factory {}
```
既然cglib是通过继承实现的，那么final修饰的方法它将无法实现代理，因为final方法是不能被overide的。  

到这里我们可以总结一下：  
- jdk动态代理的核心是通过Proxy创建代理对象，代理对象包含一个实现InvocationHandler接口的成员，最终是通过它的invoke方法反射调用实际方法
- jdk动态代理要求我们的对象必须有一个实现接口，因为它生成的代理类也要实现这个接口  
- jdk动态代理是通过反射实现的，比较简单，性能有所损耗   
- cglib的核心是通过字节码动态生成代理类，具体是使用asm字节码技术，实现较为复杂，性能较好    
- cglib比较灵活，它是通过继承实现的，不要求我们的类有实现接口   
- cglib通过继承实现覆写我们的方法，final修饰的方法cglib无法代理  

## springboot应用   
动态代理模式在spring中几乎随处可见，应用非常广泛。spring默认情况下，如果类有实现的接口，那么将使用jdk动态代理，否则将使用cglib。  
但在springboot(2.0以后)中如果我们仔细观察，无论有没有实现接口，springboot都是使用cglib来动态代理。为什么springboot要这么做呢？   
性能是一个考虑，但是spring并没有改变这个行为，证明jdk动态代理的性能还是可以的，否则spring也会替换它。  
而springboot是从灵活性来考虑的，还是我们上面的类，我们在springboot使用Autowired注入如下：  
```
	@Autowired
	private RequestHandler requestHandler;
```
无论是使用jdk还是cglib都能正常运行，但是如果是如下方式：  
```
	@Autowired
	private SimpleRequestHandler requestHandler;
```
使用jdk动态代理就会报错，因为它是基于接口和反射实现的，具体的类并没有生成对象或子类，所以没法注入。而cglib则没有问题，因为它可以生成一个子类，所以说cglib比较灵活。  
springboot这个行为是通过spring.aop.proxy-target-class参数控制的，如果显式设置为false，则会开启jdk动态代理。  

## 其它实现方式   
上面介绍的两种方式是常用的方式，也是spring框架所实现的。cglib基于字节码实现动态代理，那么其它字节码技术也可以实现类似的功能，如[javassist](https://github.com/jboss-javassist/javassist)、[Byte-Buddy](https://github.com/raphw/byte-buddy)   
cglib在github上可以看到已经很久很更新了，而javassist和Byte-Buddy都还比较活跃，三者的star数差不多。   
在面试过程中如果可以提及这点，证明知识点更广，是个加分项哦。  
