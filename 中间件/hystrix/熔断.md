## 简介   
熔断机制(circuit breaker)的思想是当达到临界条件时，触发停止操作，避免情况继续恶化，起到保护作用。临界条件是一个危险的信号，当超过这个值，如果任由其发展情况可能会越来越糟糕，最终出现雪崩现象。熔断机制也称为“自动停盘机制”，最早是出现在金融领域，当股票出现大规模崩盘时，就会触发熔断，停止交易。    

1987年10月19日，纽约股票市场爆发了史上最大的一次崩盘事件，道琼斯工业指数一天之内重挫508.32点，跌幅达22.6%，由于没有熔断机制和涨跌幅限制，许多百万富翁一夜之间沦为贫民，这一天也被美国金融界称为“黑色星期一”。   
1988年10月19日，美国商品期货交易委员会与证券交易委员会批准了纽约股票交易所和芝加哥商业交易所的熔断机制。根据美国的相关规定，当标普指数在短时间内下跌幅度达到7%时，美国所有证券市场交易均将暂停15分钟。  
熔断的机制和原理和电路的保险丝是类似的，当电流过大时，为防止对电器设备破坏，保险丝就会触发跳闸，起到保护作用。  

在程序领域，熔断机制也是非常重要的。分布式系统下调用链路可能非常复杂，当一个服务出现异常时，例如超时，就会影响上游的服务，由于请求积压，最终会导致整个链路的请求都出现异常，这就是雪崩现象。  
熔断存在的意义就是当出现这种情况时，快速失败，快速返回，不再继续调用，这样请求就不会积压，这样会出现某些功能不可用，等出现问题的服务恢复后，上游服务会自动恢复正常。有了熔断机制，这里影响的就只是一小部分功能，并且不需要人工介入就可以恢复正常。  

![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/cb-1.png)   

最近生产一个服务在短时间内报了几百个错，报错实际是一个业务方接口报错，但是对方反馈只有20个错误，而我们这边记录到的错误日志有几百个。  
服务使用了feign进行接口调用，集成了hystrix。其中有两部分错误，一种接口报的500错误，另一种是熔断报的错。   

![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/cb-2.png)  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/cb-3.png)  

## hystrix   
hystrix已经实现了熔断机制，默认开启。在hystrix中熔断有3种状态，open,close,half-open，并且可以相互流转。如图：  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/cb-4.png)  

open:熔断开启状态，当在时间窗口内请求失败次数达到一定条件，就会触发熔断开启，开启后在一定时间内的请求都会fast-fail。  
half-open:熔断处于open状态时会拒绝所有请求，但在一定时间后，可以尝试去调用，如果还是失败就继续回到open状态，如果成功就进入close状态。  
close:熔断关闭状态，正常情况处于关闭状态，请求会发出。  

**熔断核心参数**   
circuitBreaker.enabled：是否启用熔断器，默认是开启的  
circuitBreaker.requestVolumeThreshold：滑动窗口内最小请求数，默认是20  
circuitBreaker.sleepWindowInMilliseconds：熔断开启时，多久后重新尝试请求，默认是5s  
circuitBreaker.errorThresholdPercentage： 滑动窗口内失败率达到这个值就会触发熔断，默认是50  
circuitBreaker.forceOpen/circuitBreaker.forceClose：强制开启/关闭熔断，默认值都是false   

上面的参数涉及到了滑动和窗口、请求数和比率，也就是hystrix需要帮我们记录这些内容才能对熔断做出判断，那么它是记录在哪里呢？  
Metrics是hystrix的指标模块，用于收集相关指标参数，用于hystrix决策和监控。  
滑动窗口的定义如下：  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/cb-5.png)   
为了方便统计hystrix在滑动窗口内继续划分bucket(桶)，每个桶内包含了请求成功，失败，超时的统计信息。     
metrics.rollingStats.timeInMilliseconds：滑动窗口的时长，默认是10s  
metrics.rollingStats.numBuckets：滑动窗口内bucket的数量，默认是10个   

通过上面的参数我们就可以很好解释生产出现的错误日志了，因为都是在10s内出现的请求错误，刚好达到20个，触发了熔断，导致后面的请求都快速失败了。5s后对方服务恢复正常，hystrix尝试调用正常，熔断结束。   
对于并发数比较高的请求，太早出现熔断不一定就是好事，会导致后面的请求都失败了。这里可以调整circuitBreaker.requestVolumeThreshold参数，配置大一点，或者调整circuitBreaker.sleepWindowInMilliseconds参数，调整小一点，防止这种瞬间的错误触发熔断。  
hystrix参数：https://github.com/Netflix/Hystrix/wiki/Configuration#metrics.healthSnapshot.intervalInMilliseconds




