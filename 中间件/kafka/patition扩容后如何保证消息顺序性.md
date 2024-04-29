本篇来源于一道面试题，提出本人的一个解决方案。题目：以使用kafka为例，**消息队列在partition扩容后，如何保证消息的有序性。**     

在开始前我们首先回顾一下基础知识，在[kafka分区分配策略]这一篇已经对kafka的存储结构做了一些介绍。    
我们知道topic是一个逻辑概念，实际消息是存储在partition中，一个topic可以有一到多个partition，多个partition可以存储在不同的broker上，分担存储压力。   
此外，消费者可以为每个partition指定一个消费实例（进程或线程都可以），这样可以并发从各个partition拉取数据，提升消费能力。需要知道的是，消费实例数量一般不超过partition数量，因为每个partition最多被同一个消费者组内的一个消费实例处理，超过部分属于浪费。   
partition是一个队列数据结构，先进先出，所以在同一个partition内，天然可以保证消息消费的顺序性。   

![image](https://github.com/jmilktea/jtea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/kafka/images/kafka-partition-order-1.png)    

那么kafka client是如何决定将一条消息发送到哪个partition的呢？这里以使用spring kafka为例，默认使用的随机挑选一个partition，源码位置在：StickyPartitionCache.nextPartition()。  

![image](https://github.com/jmilktea/jtea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/kafka/images/kafka-partition-order-2.png)    

从上代码可以看出，会使用ThreadLocalRandom随机生成一个整数，然后取余总partition数量，得到要发送的partition。    
> ThreadLocalRandom 是 Random 的ThreadLocal版本，线程间不会有竞争，并发效率高，推荐使用。    

那么有序消息又是什么概念呢？   
我们知道数据是可以有生命周期的，这里以点餐为例，一个订单数据的状态包括：已下单 -> 商家接单 -> 配送中 -> 已收货。    
并且这个状态是有严格顺序要求的，如果乱序就可能变成：已下单 -> 商家接单 -> 已收货 -> 配送中，给用户造成不好的体验。    

如果我们使用上述的发送方式，消息就会被随机发送到一个partition，而每个partition可能被不同的消费实例消费，因此不能保证哪条消息先被消费。   
例如：用户下单后，程序发送了一条消息，此时还没被消费，商家立马接单了，也发送了一条消息，被消费了，此时消费者接收到的状态就变成先接单，再创建订单。    

前面我们说到同一个partition内，消息天然是有序的，所以如果我们可以把相同订单，发送到相同partition，这个问题就解决了。   
例如，以订单id作为key，为其选一个partition，后续这个订单的状态变更都发送到这里，那么就不会有乱序问题。   
代码如下，其中key就是用于标识的字段。   

![image](https://github.com/jmilktea/jtea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/kafka/images/kafka-partition-order-3.png)   

继续往下看，会发现如果指定了key，会使用murmur2算法，生成一个固定的hash值，然后计算得到一个partition。    
> Murmur2 哈希算法是由 Austin Appleby 在 2008 年设计的一种非加密哈希函数。它被优化用于速度，并生成 32 位的哈希值。该算法作用于一系列字节流，基于整个输入产生一个哈希值。    

![image](https://github.com/jmilktea/jtea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/kafka/images/kafka-partition-order-4.png)    

一般到这里就万事大吉了，但面试官往往要体现自己的水平，提出一些刁钻的问题，也就是本篇我们要讨论的主题。    
随着业务的发展，数据量越来越大，消费速度跟不上了，消费实例还可以增加，但由于partition数量的限制，增加也没有用，所以必须扩容partition。    

那么问题就来了，增加partition的数量，相同的key，新消息计算后的partition与存量消息(积压还没消费的)不一样，新旧消息就可能出现乱序问题。    
怎么解决呢？    
首先脱离场景讨论方案都是耍流氓，具体问题具体分析。上面说到有乱序问题是新消息与存量消息共存条件下，才会出现，如果新消息发送时，旧消息已经都被消费完了，那自然就没有这个问题了。   
那么我们可以等到系统闲时，再做扩容操作。例如商场的订单系统，商场一般晚上几点就会关门，那么就不会用用户下单了，这个时候扩容是安全的。   

这是一个最简单做法，也是许多公司用的方法，但大概率不会是面试官想听到的最终答案。例如对于电商这种访问量比较高的，凌晨都还有很多用户访问量，不可忽视。或者一些金融系统，对数据要求较高，容忍度低，不允许出错。   
这个时候扩容就比较麻烦了，没有无损的方案，这里提出本人的一个想法，仅供参考。   

我们可以新建一个topic，partition是扩容后的数量，消费逻辑和旧的是一样的，但发往这个topic的消息会先pause住，不消费。等旧的topic消费完成，再重新resume，消费新topic的数据。   

![image](https://github.com/jmilktea/jtea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/kafka/images/kafka-partition-order-5.png)    

这种做法对代码的侵入性实际也不会特别强，就是发送者需要改一下发送的topic。消费者pause/resume可以做成一个通用的功能，通过配置中心配置，即可暂停指定topic的消息消费，例如笔者所在项目者就有这个通用实现。spring MessageListenerContainer提供了pause/resume方法，可以暂停指定topic消息的消费和恢复，我们只需要跟配置中心对接起来即可。   

需要注意的是，一般我们消息会保留几天，这种做法会有两份消息日志文件，如果你对历史消息还有一些处理，就要注意了。等过一段时间，消息过期，即可下线旧的topic，清理日志文件。   
这种做法和前面说到的闲时扩容并不冲突，依然最好选择在闲时进行。此外，提前预估要partition的数量，预留多一些空间，可以减少这个麻烦。   











