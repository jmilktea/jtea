从标题上看，堆内存中还有线程私有区域？？？在我们的理解中，堆内存是共享的，所有线程都可以访问，难道还存在一块特殊的区域为每一个线程分配一个独立的空间？      
以下以hotspot虚拟机为例，通过我们将jvm内存区域划分为以下：   
![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/tlab-0.png)    

通常我们说栈、本地方法栈、程序计数器是线程私有的，堆和方法区是共享的。这句话其实不细扣也没什么问题，但如果学习jvm就会了解到堆中确实还有一块特殊的区域，就是本篇我们要介绍的**TLAB**，英文全称是thread local allocation buffer，翻译过来就是线程本地分配缓冲区。    
在面试的时候，如果你简历写了熟悉jvm，面试官也许就会这样问：   
1.jvm是如何为一个对象分配内存空间的？   
2.堆内存从分配角度上看，是否存在线程私有空间？   

如果你不写熟悉jvm，那连面试的机会都没有~

接下来我们带着这两个问题来学习TLAB。    
首先看下jvm是如何在堆中为一个对象分配内存的(这里不讨论JIT优化后的“栈上分配”)。堆内存按照年轻代、老年代可以划分如下：   
![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/tlab-1.png)   

**指针碰撞与空闲列表**    
java中一个实例对象由3部分组成，对象头、实例数据、对齐填充。对象头是固定大小的，存储了对象的hashcode，gc代龄，锁信息，类型指针等，实例数据就是对象中字段所占用的空间，对齐填充是做内存对齐保证每个对象占用空间都是8字节的整数倍。在对象创建的时候，就知道其所占用的内存大小，从而才能去堆内存申请空间。     

对于标记-整理和复制算法，内存是整齐的，那么分配的时候就是从空闲位置往后申请一块指定大小的空间，这种方式称为**指针碰撞**，如下：   
![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/tlab-2.png)   

对于标记-清除算法，例如使用CMS垃圾收集器，清除后存在内存碎片，内存碎片也是可以重新分配使用的，所以不能简单是使用指针移动的方式分配，它会维护一个空闲列表，知道哪些区域是空闲的，分配的时候会先检查能不能从**空闲列表**分配，如果可以就直接分配，否则再使用指针碰撞的方式分配。如下：  
![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/tlab-3.png)    

无论是使用指针碰撞还是空闲列表，都会存在**分配并发问题**，内存在物理逻辑上只有一块，而我们的程序是多线程的，每个线程都会去申请内存空间，那自然就会有并发问题。   
怎么解决呢？想想我们在并发编程的时候如何保证线程安全，最简单的方式就是加锁，但是加锁的效率太低，进而引申出CAS，通过轻量级的自旋提升效率，还有一种就是ThreadLocal机制，干脆为每个线程分配一块独立的空间，不共享，就不会有并发问题了。   
对于jvm内存分配也是这个思想，如果多个线程同时申请一块内存，就需要CAS看看是谁竞争到了，竞争不到的就需要重新申请，当竞争很激烈的时候，分配效率就会降，所以jvm设计了TLAB机制，相当于线程中的ThreadLocal，以此来降低分配并发竞争问题，提升内存分配速度。   

**TLAB**    
TLAB是jvm为每个线程在堆中分配的一块独立区域，在TLAB上分配内存，不同线程间不会相互影响，以此来降低内存分配的并发，提升分配速度。TLAB是一块很小的区域，属于eden区，上面堆内存划分细分如下：  
![image](https://github.com/jmilktea/jtea/blob/master/jvm/image/tlab-4.png)    

需要注意的是，上面我们提到是从分配角度上看，TLAB是线程私有的一块区域，而不是从使用的角度上，也就是说它和线程的ThreadLocal还不完全是一个意思。ThreadLocal是真正意义上的线程私有，线程间是隔离的，其它线程不能读写本线程ThreadLocal的数据，而TLAB仅仅是jvm在内存分配时线程间不相互干扰，数据其它线程该怎么读怎么写和其它内存区域是一样的，不会区别对待，包括垃圾回收，也和eden区其它内存一样，会被移动到surivivor，所以我们说从分配角度上看才能说它是线程私有的。   

相关参数：   
-XX:-UseTLAB：可以通过该参数关闭TLAB，默认是开启的。   
-XX:TLABWasteTargetPercent：可以通过该参数设置TLAB占eden区的比例，默认是1%。   
-XX:TLABSize：可以通过该参数设置TLAB大小，默认值是0，表示jvm根据运行时计算，动态调节大小。   
-XX:-ResizeTLAB：可以通过该参数关闭动态调节功能。    
-XX+PringTLAB：通过通过该参数打印TLAB gc日志。    

**refill_waste最大浪费空间**   
上面我们说到TLAB是每个线程一个很小的区域，因为线程数是非常多的，例如springboot tomcat默认就使用200个线程，所以不可能为每个线程分配很大的空间。    
那如果线程的TLAB不够了怎么办? 比如一个线程的TLAB大小是50k，使用了45k，现在线程要申请10k。这个时候有两种做法：   
1. 直接在eden中分配。这种做法可能导致后续很多分配都需要在eden中进行，失去TLAB原本意义。   
2. 放弃当前TLAB，重新申请一个。重新申请一个就最够使用了，但废弃的还有5k剩余空间，这部分会浪费掉，且jvm还需要对其进行特殊处理，方便gc回收，会有性能损失，且频繁的重新申请也会有性能影响。   

为了解决这个问题，jvm设计了refill_waste最大浪费空间参数，当TLAB空间不足时，如果要分配的大小大于最大浪费空间，则直接在eden中分配，否则重新申请一个TLAB。   
也就是当前TLAB不够用了，剩余要浪费的部分比我们配置的值小，那就可以浪费，重新申请一个TLAB，否则就不能浪费，继续使用，本次分配要在eden中进行。   
refill_waste可以通过-XX:TLABRefillWasteFraction参数配置，默认是64，表示大小为TLAB的1/64。    

最后，java虚拟机规范并没有规定TLAB的实现，所以并不是每个虚拟机都有TLAB机制，上面我们是以hotspot虚拟机为例，这也是我们最常用的java虚拟机。    



