# 前言    
关于动态代理的一些知识，以及cglib与jdk动态代理的区别，在[这一篇](https://github.com/jmilktea/jtea/blob/master/%E9%9D%A2%E8%AF%95/jdk%E5%8A%A8%E6%80%81%E4%BB%A3%E7%90%86%E4%B8%8Ecglib.md)已经介绍过，不熟悉的可以先看下。    
本篇我们来学习一下cglib的FastClass机制，这是cglib与jdk动态代理的一个主要区别。      
我们知道jdk动态代理是使用InvocationHandler接口，在invoke方法内，可以使用Method方法对象进行反射调用，反射的一个最大问题是性能较低，cglib就是通过使用FastClass来优化反射调用，提升性能，接下来我们就看下它是如何实现的。     

# 示例     
我们先写一个hello world，让代码跑起来。如下：    
```
public class HelloWorld {

	public void print() {
		System.out.println("hello world");
	}
}


public class HelloWorldInterceptor implements MethodInterceptor {
	public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
		System.out.println("before hello world");
		methodProxy.invokeSuper(o, objects);
		System.out.println("after hello world");
		return null;
	}
}
```

非常简单，就是使用MethodInterceptor在HelloWorld类print方法前后打印一句话，模拟对一个方法前后织入自定义逻辑。    
接着使用cglib Enhancer类，创建动态代理对象，设置MethodInterceptor，调用方法。   
为了方便观察源码，我们将cglib生成的动态代理类保存下来。     
```

//将生成的动态代理类保存下来
System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "D:\\");

Enhancer enhancer = new Enhancer();
enhancer.setSuperclass(HelloWorld.class);
enhancer.setCallback(new HelloWorldInterceptor());

HelloWorld target = (HelloWorld) enhancer.create();
target.print();
```

输出
```
before hello world
hello world
after hello world
```

# FastClass机制    
我们知道cglib是通过继承实现的，动态代理类会继承被代理类，并重写它的方法，所以它不需要像jdk动态代理一样要求被代理对象有实现接口，因此比较灵活。    
既然是通过继承实现的，那应该生成一个类就可以了，但是通过上面的路径观察，可以看到生成了3个文件，其中两个带有FastClass关键字。       
这三个类分别是：动态代理类，动态代理类的FastClass，被代理对象的FastClass，从名称上也可以看出它们的关系。     
![image](1)     

其中动态代理类继承了被代理类，并重写了父类的所有方法，包括父类的父类的方法，包括Object类的equals方法和toString方法等。    
```
public class HelloWorld$$EnhancerByCGLIB$$49f9f9c8 extends HelloWorld implements Factory {
}
```

这里我们只关注print方法，如下：   
![image](2)    

第一个直接调用父类方法，也就是被代理对象的方法；第二个会先判断有没有拦截器，如果没有也是直接调用父类方法，否则调用MethodInterceptor的intercept方法，对于我们这里就是HelloWorldInterceptor。    
看下intercept的几个参数分别是什么，这几个参数的初始化在动态代理类的静态代码块中都可以找到。   
第1个表示动态代理对象。   
第2个是被代理对象方法的Method，就是HelloWorld.print。   
第3个表示方法参数。    
第4个是MethodProxy对象，通过名字我们可以知道它是方法的代理，每一个方法都会有一个对应的MethodProxy，它包含被代理对象、代理对象、以及对应的方法元信息。     

这里我们重点关注MethodProxy，它的初始化如下：    
```
CGLIB$print$0$Proxy = MethodProxy.create(var1, var0, "()V", "print", "CGLIB$print$0");       
```
第1个参数表示被代理对象的Class。      
第2个参数表示动态代理对象的Class。        
第3个参数是方法的返回值。   
第4个参数表示被代理对象的方法名称。   
第5个参数表示对应动态代理对象的方法名称。      

MethodProxy对象创建好后，我们上面就是通过它进行调用的    
```
methodProxy.invokeSuper(o, objects);
```

invokeSuper主要源码如下：     
```
public Object invokeSuper(Object obj, Object[] args) throws Throwable {
    init();
    FastClassInfo fci = fastClassInfo;
    return fci.f2.invoke(fci.i2, obj, args);
}

private void init()
{
    if (fastClassInfo == null)
    {
        synchronized (initLock)
        {
            if (fastClassInfo == null)
            {
                CreateInfo ci = createInfo;

                FastClassInfo fci = new FastClassInfo();
                fci.f1 = helper(ci, ci.c1); //被代理对象的FastClass
                fci.f2 = helper(ci, ci.c2); //动态代理对象的FastClass
                fci.i1 = fci.f1.getIndex(sig1); //被代理对象方法的索引下标
                fci.i2 = fci.f2.getIndex(sig2); //动态代理对象方法的索引下标，这里是：CGLIB$print$0 
                fastClassInfo = fci;
                createInfo = null;
            }
        }
    }
}
```
init方法使用加锁+双检查的方式，只会初始化一次fastClassInfo变量，它用volatile关键字进行修饰，这里涉及到java字节码重排问题，具体可以参考我们之前的分析:[happend before原则](https://github.com/jmilktea/jtea/blob/master/%E5%9F%BA%E7%A1%80/happend%20before%E5%8E%9F%E5%88%99.md)     

接着回到invokeSuper方法，fci.f2.invoke(fci.i2, obj, args); 实际就是调用动态代理对象的FastClass的invoke方法，并把要调用方法的索引下标i2传达过去。   
至于方法的索引下标是怎么找到的，可以看动态代理对象的FastClass的getIndex方法，其实就是通过方法的名称、参数个数、参数类型，完全匹配，点到源码文件可以看到有大量的switch分支判断。    
这里我们可以看到print方法的索引下标就是18。     
```
public int getIndex(String var1, Class[] var2) {
    switch (var1.hashCode()) {
        case -1295482945:
            if (var1.equals("equals")) {
                switch (var2.length) {
                    case 1:
                        if (var2[0].getName().equals("java.lang.Object")) {
                            return 0;
                        }
                }
            }
            break;
        case 770871766:
            if (var1.equals("CGLIB$print$0")) {
                switch (var2.length) {
                    case 0:
                        return 18;
                }
            }
            break;
    }
}
```
```
 public Object invoke(int var1, Object var2, Object[] var3) throws InvocationTargetException {
    HelloWorld..EnhancerByCGLIB..49f9f9c8 var10000 = (HelloWorld..EnhancerByCGLIB..49f9f9c8)var2;
    int var10001 = var1;

    //...
    switch (var10001) {                
        //...
        case 18:
            var10000.CGLIB$print$0();
            return null;
    }
 }    
```

可以看到最终调用到动态代理类的CGLIB$print$0方法，也就是：
```
    final void CGLIB$print$0() {
        super.print();
    }
```
最终调用的就是父类的方法。我们画张图总结一下，有兴趣的同学跟着图和代码逻辑应该可以快速理解。    
![image](3)     


# 总结    
经过上面的分析，我们可以看到cglib在整个调用过程并没有用到反射，而是使用FastClass对每个方法进行索引，通过方法名称，参数长度，参数类型就可以找到具体的方法，因此性能较好。但也有缺点，首次调用需要生成3个类，会比较慢。      
另外MethodProxy还有一个invoke方法，如果我们换一下调用这个方法会发生，留给大家自己尝试。    
```
	methodProxy.invokeSuper(o, objects);
    //换成 methodProxy.invoke(o, objects);
```
















