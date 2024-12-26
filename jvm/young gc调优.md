# 背景
本篇主要介绍笔者最近的一次young gc优化，我们生产上有个核心服务，上游对接C端，下游对接算法模型，调用量比较大，对性能要求高。     
目前我们部署了10个节点，主要参数如下：最大堆为6G，按照比例，新生代为2g，老年代为4g。容器资源限制为内存8G，cpu 4个。tomcat线程为1000。     
```
java -Xms2g -Xmx6144m -Xss256k

resources:
    limits:
        cpu: '4'
        memory: 8Gi
    requests:
        cpu: '4'
        memory: 8Gi
```

**请求调用量和请求耗时**  

![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/ygc-1.png)    

![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/ygc-2.png)    

从监控上看：   
1.调用量比较高，达到1w+。    
2.每天都有几个峰值。    
3.接口响应都比较快，基本都在几十毫秒。   

从上面看出服务整体配置和响应都是不错的，但开发同学反应难以缩容，甚至有时候还要扩容，否则有时候请求P99时间会上升。    
经验告诉我们，这很可能是垃圾回收的影响，所以我们主要排查一下gc的情况是否达到最优。    

# gc情况
我们主要关注的是吞吐量和延迟，jkd8没有设置gc参数的话，默认采用的是Parallel并行收集器，通过**jmap -heap pid**查看。

![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/ygc-3.png)   

parallel gc关注的是吞吐量，**吞吐量 = 运行用户代码时间  / (运行用户代码时间 + gc时间)**，当然吞吐量越高表示运行用户的代码时间越多，越好。我们挑选一台机器进行观测：

young gc次数，每分钟最大6次左右。

![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/ygc-4.png)    

young gc时间，每分钟最大1.5s左右。

![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/ygc-5.png)    

full gc，10几天才有1次full gc，且回收大量对象。

![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/ygc-6.png)    

简单总结一下：

1.年轻代回收时间似乎有点不正常，时间久。    
2.老年代一gc，99%的对象被回收掉。    

结合jstat看一下，命令：jstat -gc 1 2000 60，结果：

![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/ygc-7.png)    

![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/ygc-8.png)    

有几个关键信息：

1.s0，s1的空间很小，没有按照8:1:1的比例取分配2G的新生代。出现这点的原因是parallel gc是有自我调节能力的，**-XX:+UseAdaptiveSizePolicy**默认是开启，开启后jvm会根据每一次gc的情况，调整eden和survivor的比例。
这里是由于老年代gc相比新生代gc几乎为0，所以jvm认为对象基本都在年轻代回收了，不需要大的survivor区，避免浪费空间。   
> 以后在面试的时候可以告诉面试官这个比例并不是固定的

2.每次晋升到老年代的对象很少。通过s0,s1和ou可以看出，每次晋升的对象很小，大概只有几M。这也符合上面的监控图，老年代以非常缓慢的速度在上升。

3.每次young gc的时间久，要200多毫秒，这点非常糟糕！

在我多次用上面的jstat观测后，有这样的结果：

发生gc 10次，耗时6243.936 - 6242.068 = 1868ms，平均每次186.8000ms，吞吐量为(120 -1.868) / 120 = 98.44%  
发生gc 8次，耗时6265.155 - 6263.662 = 1493ms，平均每次186.6000ms，吞吐量为 (120 -1.868) / 120 = 98.44%

新生代gc次数比较高是可以理解的，但每次200ms有点出乎意料，实在太糟糕了，需要优化。     

# young gc 分析
parallel young/old 垃圾回收器都只有两个阶段，标记 → 回收，且整个过程会STW，不像CMS或G1有阶段会与用户线程并发。    

![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/ygc-9.png)    

按照经验，逐步排查以下情况：

- gc线程过多，导致线程切换？  
在cpu有限的情况下，gc线程过大，在并发回收时可能导致线程频繁切换，耗时增加。
从上面jmap结果可以看出，只使用了4个gc线程，且jstack 1 | grep GC 也是4个，正常。

- 年轻代过大，导致回收时间长？   
正常来说，年轻代越大，gc时间越长，但不会和堆大小等比增长。   
较小的年轻代，理论上可以降低一些时间，但也会使吞吐量下降。  
例如越来10s gc1次，耗时200ms，降低一半空间后，5s gc 1次，每次150ms，吞吐量下降了。

- 代码问题，创建了过多/过大的对象？   
排查代码性价比就很低了，不过还是看了一遍，看了一下调用量最高的接口方法。发现有一处写法：   
new Gson().fromJson(jsonObject, DataVo.class);   
gson对象重复创建，一个gson对象大概占用832个字节，相当于每次浪费52个Object。    
对象的内存估计用到的技术：1.ObjectSizeCalculator.getObjectSize(new Gson()); 2.JOL。  
同样还是一些其它写法问题可以优化，但结合调用的量计算，就算每次多创建这些对象，也不会对gc有本质影响。    

- 更换垃圾回收器为G1    
G1的特点是时间可控，因为它可以回收部分内存，而不是整个新生代或老年代，例如设置MaxGcPauseMillis为100ms，G1将尽量在这个时间内优先回收垃圾较多的region，以取得回收内存和STW时间的一个平衡。  
当前的配置理论上使用G1会更加合适，不过切换为G1也需要验证，且没有找到根本原因。推荐参数：  
```
-Xmx5440M -Xms5440M -XX:MaxMetaspaceSize=512M -XX:MetaspaceSize=512M -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ParallelRefProcEnabled -XX:ErrorFile=/data/logs/hs_err_pid%p.log -Xloggc:/data/logs/gc.log -XX:+PrintGCDetails -XX:+PrintGCDateStamps
```
jvm参数生成：https://opts.console.heapdump.cn

问题陷入僵局，回到young gc的回收过程，回收采用的是标记-复制算法，标记是指从根节点出发，进行可达性分析，这个过程通常很快。以下对象可以作为gc root：

- Class，由系统类加载器加载的类型对象，这些对象不会被卸载，它们所持有的静态字段（存放在堆中，静态属性所引用的对象）
- 常量池中的对象(存放在堆中的字符串常量池)
- Thread，存活的线程
- Stack Local，线程栈中的引用对象
- 本地方法栈中的引用对象
- 同步锁对象

复制是将存活的对象复制到suvivor或老年代，这个过程的耗时与要复制的对象大小有关，从上面jstat结果也可以看出，每次复制的对象很小，这个阶段的耗时也非常小。    
复制这个阶段耗时很小是有实际证据支撑了，不过标记过程只有理论支撑，那会不会是这里出了篓子？   
经过一番查找，发现还有一些特殊的场景会导致新生代扫描阶段耗时过程：**跨代引用**和**StringTable**。    

# 跨代引用
先提出一个问题，在young gc时，是否只要扫描新生代就可以了？答案是否定的，也需要扫描老年代，因为可能出现跨代引用。如下：

![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/ygc-10.png)    

如果只扫描新生代，你会发现A2是垃圾，但它实际被老年代的对象引用着，是不能被回收的，这就是跨代引用。    
那么解决方案也非常简单，在young gc的时候把老年代也可达性分析一遍就可以了，但这样效率太低了，想想young gc要扫描整个老年堆，效率是非常低的。    

java虚拟机通过**记忆集RememberSet**解决这个问题，记忆集建立在新生代的一种数据结构，用于记录从非收集区域指向收集区域的指针集合，记忆集可以看做是一个“接口”或一种“规范”，不同垃圾回收器对它可以有不同的实现，这里要提到的实现之一就是**卡表**。    

在hotspot虚拟机，卡表将内存划分为n个大小为512字节的区域，通过0和1表是该页是否为脏页，1则表示为脏页，该区域内有对象存在跨代引用，则改区域内全部对象需要加入到gc root去扫描，这种方式只需要通过一个Bit位则可以表示该区域是否存在跨代引用，节约内存空间。    
卡表的更新是发生在对象跨代引用时，虚拟机是通**写屏障**实现的，写屏障就是在为对象赋值前后加一些判断逻辑，可以看做类似spring里的AOP，虚拟机这种方式会导致对象赋值的效率下降，但提升gc的效率。    

![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/ygc-11.png)    

需要注意的是，上面卡表是建立在新生代，记录老年代指向新生代的集合，那如果反过来呢？老年代gc时，如果新生代引用了老年代对象，也是跨代引用，Parallel Old，CMS，G1这些老年代的垃圾回收器是怎么解决的呢？这里留给读者思考。

理论上来说，跨代引用这种一般是比较少出现的才对，所以问题大概率不在这里。  

> 关于记忆集与卡表，《深入理解Java虚拟机》有一个章节对它详细介绍，有兴趣的可以看下。   
  
# StringTable
StringTable是字符串常量池，存储在堆中，目的是缓存常用的字符串对象引用，避免重复创建字符串对象，节约内存。   
例如当我们使用字符串字面量，或者使用String.intern()方法时，会先从StringTable获取，如果没有就创建string对象，然后将引用记录到StringTable，有则直接返回对应的引用。new String()这种创建方式则是每次都在堆中创建一个新的对象。 

StringTable的回收时间是在full gc，且为了保证新生代的string对象不被回收，young gc需要扫描StringTable，所以当StringTable过大时，会导致young gc扫描耗时过长。    
通过jmap -heap pid可以查看StringTable的使用情况，我在生产执行时发现到打印StringTable时耗时非常久，直到超时，猜想StringTable应该很大了。    
通过jmap -histo[:live] pid可以触发一次full gc，此时StringTable会被回收，jmap -heap pid则可以快速打印出来，且多次打印可以观察到StringTable增长得比较快。      

那么是什么导致StringTable一直增长呢？一般业务代码不会主动调用intern()方法，字面量的创建又是无法避免的，难以优化。    

还有一种典型的案例就是json，以jackson为例，在序列化key的时候，由于key（java里的属性）一般是固定的，所以这个时候使用StringTable是可以获益的，jackson通过InternCache缓存，代码：com.fasterxml.jackson.core.util.InternCache。

![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/ygc-12.png.png)    

测试，在InternCache.inern方法打个断点观察：
```
 String json = "{\"abc\":123}";
 Map map = JSONUtil.string2Obj(json, Map.class);
```

那么key如果是随机的，那将导致大量的随机字符串进入StringTable，该服务与上游app和下游模型交互，报文很大，有很多map，主要是透传，很符合出问题的场景，通过查找相关日志发现确实存在许多随机的key。所以使用json最好不要用随机字符串作为key，也让不要让上下游这么做。        
jackson也提供了objectMapper.getFactory().disable(JsonFactory.Feature.INTERN_FIELD_NAMES); 可以禁止key的缓存，不过这样禁止会导致固定的key也不做缓存，堆内存使用会上升很多。     

![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/ygc-13.png)  

# 解决方案    
如果是上面导致的问题，那么可以猜测young gc的时间是跟这老年代的增长而不逐步增长的，也就是说在fullgc后，young gc开始的时间其实是很快的，但随着StringTable的增加，扫描耗时增加。这样我们拉长时间看一下监控，如图：    

![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/ygc-14.png.png)    

![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/ygc-15.png.png)  

可以看到这个时间线和老年代的gc是对得上的，young gc在前面几天回收时间都在50ms以内，后逐步增加，和我们的猜测完全对应上。   
也可以通过jmap -histo[:live] pid除非一次full gc，然后观察young gc的耗时就降下来了。

问题找到了，如何解决呢？   

1.禁用jackson的key缓存    
这会导致固定的key也无法使用缓存，相同的key都创建字符串对象，可能导致堆内存使用率生高很多，进而gc更加频繁，得失效果无法衡量。    

2.上下游排查整改    
不要传这种随机key的json字符串。    

3.减少老年代的大小    
当前应用老年代有4G，从上图可以看出，每次回收后大概只剩200M左右，也就是大量的空间被回收，其实没必要预留那么多的，那么可以减少老年代的大小。   
减少老年代的大小，StringTable会快一点被回收，这样新生代也许还没到最高耗时，或高耗时持续的时间会比较短，吞吐量会上升。   

减少老年代的大小会使full gc更加频繁，耗时更加久，影响性能？   
从jstat上看，应用进行了6次full gc，总共消耗1.056s，每次约170ms。从监控上看2周左右会进行一次full gc，假设我们缩小老年代大小为一半，理论上一周会发生一次，由于老年代空间减少，老年代基于标记-整理算法，回收时间也会等比减少。  
所以减少老年代的大小会使full gc更加频繁，但仍非常少，相比新生代的回收次数仍接近于0，且耗时会比现在低。    

![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/ygc-16.png.png)    

所以我们先对一台机器jvm参数进行调整，降低老年代的内存空间：

2.1 -Xms4g -Xmx4g -XX:NewRatio=1 -Xss256k    
将堆大小设置为4g，新生代:老年代=1:1，即新生代大小保持不变，老年代降低2g。-Xms -Xmx设置为相等，避免内存使用超过Xms时，频繁申请耗时。

2.2 -Xms3g -Xmx3g -Xmn=2g -Xss256k    
比较激进，直接将老年代大小降为1g，从监控上仍是可行的，降为1g后5天左右将进行一次full gc。


观察一段时间，如果表现正常，再逐步将其它节点也修改。理想情况下达到的目标是：   
1.young gc的时间降低，稳定在50ms，不超过100ms。   
2.full gc次数变多，5-7天一次，耗时变小。   
3.降低2-3g内存资源的使用。     

