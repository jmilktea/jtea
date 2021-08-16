## 简介    
前面我们介绍了hystrix的熔断，知道熔断可以起到保护作用，避免出现“服务雪崩”，往更糟糕的方向发展。hystrix是“豪猪”的意思，这家伙长相图，浑身都是刺，自卫能力非常强，名字非常贴切。     
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/res4j-1.png)   

hystrix是netflix众多套件中的一员，已经被spring cloud集成很久了，但是hystrix已经不再更新，处于维护阶段。     
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/res4j-2.png)   

从github上可以看到，hystrix代码已经3年没有更新了。并且官方推荐了resilience4j作为替代者。并且由于nexflix的停止更新，springcloud在新版本中移除了各个netflix组件的依赖，包括zuul,ribbon,hystrix等(但这并不妨碍我们对它们的学习)。      
[resilience4j](https://github.com/resilience4j/resilience4j)有“恢复”，“还原”的意思，与应用场景也比较贴切。它受hystrix的启发，利用java8的函数编程，并且设计得更加轻量、高效，它不像hystrix有那么多的外部依赖，非常简洁。在[hystrix篇](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/%E7%86%94%E6%96%AD/hystrix.md)前面我们已经介绍了熔断的原理，包括OPEN,HALF-OPEN,CLOSE三种状态和它们之间的流转，这在resilience4j基本是一样的，如图：  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/res4j-3.png)  
与hystrix不同的是，处于HALF-OPEN时，resilience4j可以配置执行次数和阈值来更灵活的调整，而hystrix是仅通过一次就做出判断。举个例子：  
当熔断器处理OPEN时，所有请求会fast-fail，一定时间后，hystrix会进入half-open状态，尝试一次调用，如果调用成功，则进入CLOSE，否则回到OPEN。  
而对于resilience4j，进入half-open后，可以配置至少执行10次，5次成功，才进入CLOSE，否则回到OPEN。可以看到这除了更加灵活，还可以通过多尝试几次，提升概率，减少一些误判。  
另外，resilience4j也可以在许多请求出现超时的时候先触发熔断，而不通用等到请求超时报错。举个例子：  
我们配置了调用外部服务5s超时，那么resilience4j可以配置当多数请求超过3s的时候就触发熔断，而不用等到请求5s超时才报错。  
与hystrix的更多不同参考：[这里](https://resilience4j.readme.io/docs/comparison-to-netflix-hystrix)     

**Ring Bit Buffer**   
在统计请求执行情况时，hystrix采用的是滑动窗口的方式，而resilience4j采用的是环形位图的方式。这有什么优点呢？    
通常我们记录一个请求的成功/失败，最简单的方式就是用一个boolean来表示，那假如需要统计1w个就需要1w个boolean。而使用位图的话，一个字节有8位，每个位置0,1就可以表示成功与否了，大大节约了存储空间。  
例如一个long占用8个byte，对应64个bit，就可以表示64个请求状态，位图特别适合这种只有0,1状态场景的统计，在redis中也有对应的实现。  
那么为什么要采用环形呢，用数组不行吗？和上面的表达类似，如果采用数组，我们还是得选择数组元素的类型，最少消费还是1个byte，所以resilience4j自己实现了ring这种数据结构。  

![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/res4j-4.png)  

## resilience4j-circuitbreaker
[官方文档](https://resilience4j.readme.io/v0.17.0/docs/circuitbreaker)     
从github上可以看到resilience4j采用的是分模块的形式，例如：熔断，限流，重试，缓存等，可以按需引入，当然也可以用all包全部引入。
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/res4j-5.png)

这里我们主要关注熔断circuitbreaker，springcloud已经集成了它。[demo地址](https://github.com/resilience4j/resilience4j-spring-cloud2-demo)    

先看下我们示例的参数
```
# 开启熔断
feign.circuitbreaker.enabled=true
# 允许open->half open的状态转换
resilience4j.circuitbreaker.configs.default.automatic-transition-from-open-to-half-open-enabled=true
# 配置熔断阈值，50表示有50%的请求失败时
resilience4j.circuitbreaker.configs.default.failure-rate-threshold=50
# 表示至少有10个请求，结合第一个参数就是：至少10个请求，50%失败就触发熔断
resilience4j.circuitbreaker.configs.default.ring-buffer-size-in-closed-state=10
# OPEN持续时间，该时间内请求都会直接熔断，超过这个时间会尝试调用
resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state=3s
# 表示至少6个请求，结合第一个参数就是：至少6个请求，50%失败就继续回到OPEN状态，否则进入CLOSE状态
resilience4j.circuitbreaker.configs.default.ring-buffer-size-in-half-open-state=6  
# 基于时间类型的窗口计算，还可以基于次数计算  
resilience4j.circuitbreaker.configs.default.sliding-window-type=TIME_BASED  
# 表示时间窗口大小为10s
resilience4j.circuitbreaker.configs.default.sliding-window-size=10   
```

结合上面的配置参数，我们看如下代码  
```
        for (int i = 0; i < 100; i++) {
			int p = i;
			try {				
				if (i == 21) {
					Thread.sleep(3000);
				}
				if (i == 31) {
					Thread.sleep(3000);
				}
				if (i >= 31) {
					p = 0;
				}
				service2Client.test(p);
				System.out.println("success:" + i);
			} catch (Exception e) {
				System.out.println("fail:" + i);
			}
		}
```
serviceClient.test接口的逻辑很简单，p % 2 == 0 接口就返回成功，否则抛出异常。结合上面的参数我们希望得出如下效果：  
0-9,会调用接口，并触发熔断  
10-20,会直接熔断，不会发起接口调用    
21-26,会调用接口，然后又回到熔断状态  
27-30,又会直接熔断  
大于30的，会调用接口，然后进入正常状态    

得到的效果和期望是一致的如下  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/res4j-6.png)    







