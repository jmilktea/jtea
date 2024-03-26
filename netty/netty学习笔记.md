# 3种IO   
**BIO**：同步阻塞IO，在发起IO操作时，应用程序会阻塞，一直等到有事件发生时才返回。使用时一个连接需要开启一个线程，当连接较多时，线程资源占用高，会有大量cpu线程切换。   

**NIO**：同步非阻塞IO，非阻塞指的是发起IO操作时，程序会立刻返回，同步指的是当数据到来，数据拷贝到用户空间这一步是同步等待的。最常见的IO多路复用就是NIO，IO多路复用是指用一个线程监听多个IO通道的事件，即可以用少量的线程处理大量的连接。    

**AIO**：异步非阻塞IO，相比NIO，AIO是数据已经拷贝到用户空间，由内核通知应用程序可以直接读取了，程序不需要同步等待数据了。AIO的实现复杂，操作系统的支持有限，现在的应用较少。   

NIO三个核心概念：Selector，Channel，ByteBuffer。    
**Selector**：Selector负责监听注册在它上面的所有IO事件，当有IO事件发生时，返回给程序。一个Selector可以监听多个Channel的事件，由一个线程负责，即可实现一个线程管理多个连接。   

**Channel**：通道，可以操作IO事件，通道建立后，就会注册到Selector上，由Selector进行监听。常见的Channel有：FileChannel、SocketChannel、ServerSocketChannel。  

**Buffer**：缓存区，所有的数据都是经过ByteBuffer，读写到Channel通道。ByteBuffer是常用的Buffer，支持读写切换。ByteBuffer又分为HeapByteBuffer表示基于堆内存的缓存区，MappedByteBuffer基于堆外内存的缓存区。   

# select/poll/epoll   
这三个函数是实现linux io多路复用的内核函数。   
linux最开始提供的是select函数，方法如下：
```
select(int nfds, fd_set *r, fd_set *w, fd_set *e, struct timeval *timeout)
```
该方法需要传递3个集合，r,e,w分别表示读、写、异常事件集合。集合类型是bitmap，通过0/1表示该位置的fd(文件描述符，socket也是其中一种)是否关心对应读、写、异常事件。例如我们对fd为1和2的读事件关心，r参数的第1,2个bit就设置为1。  

用户进程调用select函数将关心的事件传递给内核系统，然后就会阻塞，直到传递的事件至少有一个发生时，方法调用会返回。内核返回时，同样把发生的事件用这3个参数返回回来，如r参数第1个bit为1表示fd为1的发生读事件，第2个bit依然为0，表示fd为2的没有发生读事件。用户进程调用时传递关心的事件，内核返回时返回发生的事件。   

select存在的问题：
1. 大小有限制。为1024，由于每次select函数调用都需要在用户空间和内核空间传递这些参数，为了提升拷贝效率，linux限制最大为1024。
2. 这3个集合有相应事件触发时，会被内核修改，所以每次调用select方法都需要重新设置这3个集合的内容。   
3. 当有事件触发select方法返回，需要遍历集合才能找到就绪的文件描述符，例如传1024个读事件，只有一个读事件发生，需要遍历1024个才能找到这一个。    
4. 同样在内核级别，每次需要遍历集合查看有哪些事件发生，效率低下。   

poll函数对select函数做了一些改进    
```
poll(struct pollfd *fds, int nfds, int timeout)

struct pollfd {
	int fd;
	short events;
	short revents;
}
```

poll函数需要传一个pollfd结构数组，其中fd表示文件描述符，events表示关心的事件，revents表示发生的事件，当有事件发生时，内核通过这个参数返回回来。   
poll相比select的改进：  
1. 传不固定大小的数组，没有1024的限制了（问题1）   
2. 将关心的事件和实际发生的事件分开，不需要每次都重新设置参数（问题2）。例如poll数组传1024个fd和事件，实际只有一个事件发生，那么只需要重置一下这个fd的revent即可，而select需要重置1024个bit。   

poll没有解决select的问题3和4。另外，虽然poll没有1024个大小的限制，但每次依然需要在用户和内核空间传输这些内容，数量大时效率依然较低。   

这几个问题的根本实际很简单，核心问题是select/poll方法对于内核来说是无状态的，内核不会保存用户调用传递的数据，所以每次都是全量在用户和内核空间来回拷贝，如果调用时传给内核就保存起来，有新增文件描述符需要关注就再次调用增量添加，有事件触发时就只返回对应的文件描述符，那么问题就迎刃而解了，这就是epoll做的事情。   

epoll对应3个方法  
```
int epoll_create(int size);
int epoll_ctl(int epfd, int op, int fd, struct epoll_event *event);
int epoll_wait(int epfd, struct epoll_event *events, int maxevents, int timeout);
```
epoll_create负责创建一个上下文，用于存储数据，以后的操作就都在这个上下文上进行。 
epoll_ctl负责将文件描述和所关心的事件注册到上下文   
epoll_wait用于等待事件的发生，当有有事件触发，就只返回对应的文件描述符了。  

epoll有LT、ET两种默认，默认是LT模式。LT是调用epoll_wait方法有事件触发时应用程序可以不处理事件，下次调用时epoll会再次通知该事件。ET是如果应用程序不处理事件，下次就不再通知。

# netty简介
netty是一个高性能、易扩展、社区活跃的网络开发框架。    
1、使用原生的java NIO，编码复杂，要实现一个完整的网络开发框架需要大量的工作，如网络协议，编解码，粘包拆包，这些netty都有很好的支持。   
2、原生的java NIO有著名的epollo空轮询bug，netty对其进行处理。   
3、netty的设计比较优雅，容易扩展，使开发者不需要关注过多复杂的网络概念就可以进行开发，例如开发者通常只需要关注ChannelHandler。   
4、netty的社区非常活跃，经过大量的生产验证，ElasticSearch、Dubbo、Rocketmq、HBase、spring webflux，gRPC都用了netty。    
5、相比Mina，Netty是后来居上者，经过重新设计，相比Mina要更加优秀。Grizzly出自Sun公司，应用较少。   
目前netty用得多的版本是4.x，5.x版本由于有重大bug被官方废弃。   

整体架构：   
![image](0)    

# reactor模型     
reactor模型是基于事件的处理模型，在很多中间件都有使用到reactor模型，nginx、nodejs、redis等。
doug lee在[scalable io in java](https://gee.cs.oswego.edu/dl/cpjslides/nio.pdf)总结了三种reactor模型，netty对这三种模型都能支持。   
1、**单reactor单线程**。整个过程由一个线程完成，包括创建连接，读写数据，业务处理。redis 6.0以前的版本就是这种模式，实现起来简单，没有线程切换，加锁的开销。缺点是单线程不能发挥多核cpu的优势，如果有一个业务处理阻塞了，那么整个服务都会阻塞。   
2、**单reactor多线程**。接收连接(accept)和IO读写还是由一个线程完成，但业务处理会提交给业务线程池，业务处理不会阻塞整个服务。    
3、**多reactor多线程**。接收连接由一个main reactor处理，建立连接后将其注册到sub reactor上，每个sub reactor都是单reactor多线程模式。    
netty通过设置boss EventLoopGroup，worker EventLoopGroup线程数，即可轻松实现上述3种reactor模型。    

# netty中的核心对象   
**Bootstrap**，引导对象，负责配置启动参数，串联各个组件，netty服务的启动都是从配置Bootstrap开始的。Bootstrap有代表客户端的Bootstrap和代表服务端的ServerBootstrap。     

**EventLoopGroup与EvnetLoop**，事件循环器。EventLoopGroup表示一组EventLoop，每个EventLoop会分配一个线程，并关联一个Selector和一个任务队列TaskQueue。EventLoop不仅会处理**IO事件**，还会执行任务队列里的**普通任务**和**定时任务**，它提供一个ioRatio比例表示执行IO和任务队列的时间比例。(select方法是可以设置一个超时时间的，超过这个时间就返回，所以不会一直阻塞，普通任务才有时间执行。也可以使用selectNow()获取IO事件，并立刻返回)       
EvevtLoop会轮询注册在它上面的Seletor的所有Channel的IO事件，并交给Pipeline处理。    
默认情况下，每个EventLoopGroup会分配cpu核数 * 2个EventLoop，也就是线程数。   

**Channel通道**，与java NIO的Channel类似，Channel会注册到EventLoop上，提供各种IO操作，如connect,read,write,flush。netty中有代表TCP协议的异步NioServerSocketChannel，NioSocketChannel，同步的OioServerSocketChannel，OioSocketChannel。代表UDP协议的NioDatagramChannel，OioDatagramChanel。    
>一个EventLoopGroup可以包含多个EventLoop，一个EventLoop关联一个Selector，一个EventLoop分配一个线程，可以管理多个Channel。    

**Pipeline,ChannelHandlerContext,ChannelHandler**，管道和处理器，每一个Channel都会关联一个Pipeline，请求会在Pipeline内流转，经过一系列的ChannelHandler处理，这是责任链模式。一个Pipeline内部维护了一个双向链表构成的ChannelHandlerContext，ChannelHandlerContext是对ChannelHandler的封装，通过它可以拿到Pipepline,Channel,Handler等信息。   
请求会经过每一个ChannelHandlerContext然后执行对应的ChannelHandler逻辑。   
Handler分为**ChannelInboundHandler**和**ChannelOutboundHandler**，Inbound表示入站处理器，请求进来时会从Pipeline的ChannelHandlerContext头节点开始传播，经过一系列的ChannelInboundHandler，最后到尾节点。写回时，从尾结点开始传播，经过一系列的ChannelOutboundHandler出站处理器，最后到头节点，返回数据给客户端。常见的入站出站处理器如ByteToMessageHandler和MessageToByteHandler。     

>总结：Boos EventLoopGroup会创建EventLoop，绑定一个Selector，然后创建NioServerSocketChannel，注册到Selector上，负责监听客户端连接。  
当有连接加入时，Boos EventLoop会触发channelRead事件，然后被ServerBootstrapAcceptor处理器捕获，ServerBootstrapAcceptor会根据本次连接创建SocketChannel，然后分配给Worker EventLoopGroup。   
Worker EventLoopGroup会选择一个EventLoop，将SocketChannel注册到它的Selector上，等待IO事件的到来。当有IO事件发生时，Worker EventLoop会通过Channel将数据读取出来，然后交给Pipeline处理。   
Pileline会创建一个ChannelHandlerContext，然后开始入站操作，经过一系列的ChannelInboundHandler处理后，写回数据，再经过一系列的ChannelOutboundHandler，最终返回数据。      

>思考，根据reactor模型，Boos EventLoopGroup负责接收连接，一个线程就够了，还有必要开多线程吗？通常是可以不用的，设置一个线程即可，但是当应用需要监听多个端口，或者还有其它任务要处理，那设置多个线程就有用了。

整体架构：  

![image](https://github.com/jmilktea/jtea/blob/master/netty/image/netty-learn-1.png)

# netty中的ByteBuf    
netty的ByteBuf是java ByteBuffer的升级，相比之下更加强大。    
1、ByteBuffer不支持扩容，分配的空间过大容易造成浪费，过小容易抛异常。
ByteBuf则可以动态扩容。  
2、ByteBuffer每次切换读写都得调用flip方法，比较麻烦。  
ByteBuf读写使用了不同索引，因此读写不需要切换。  
3、ByteBuffer用完则弃，每次使用需要分配一个新的。   
ByteBuf支持池化，用完可以缓存起来，复用。     
4、当有多个ByteBuffer需要合并的时候，是创建一个更大的buffer然后拷贝过去，效率较低。
ByteBuf体系下有CompositeByteBuf，支持零拷贝多个buffer，其原理是维护每个子buffer的引用和它们的读写索引，不会涉及到移动数据。

# netty是如何解决jdk epoll空轮询的bug的？   
java NIO对epoll的封装是有bug的，且这个bug经过优化后还一直存在，只是概率减少了。bug的出现是在调用select方法时，底层调用epoll，本应阻塞返回发生的事件，但确返回了空，返回空就不处理，继续while循环，导致cpu 100%。这是jdk的bug，netty并没有解决，而是通过检测的方式巧妙避开。具体做法是netty会维护一个selectCnt，每次空轮询后会+1，当达到一定阈值（512）后，就重建selector，并将老的selector上的channel重新注册到新的selector上，废弃老的selector。具体代码如下，主要是通过selectRebuildSelector重建selector。     
```
//模拟出现bug的代码
while(true){
    int keys = selector.select(); //本应阻塞
    if(keys == 0){
        continue;
    }
    //...
}
```
```
long time = System.nanoTime();

if (time - TimeUnit.MILLISECONDS.toNanos(timeoutMillis) >= currentTimeNanos) {

    selectCnt = 1;

} else if (SELECTOR_AUTO_REBUILD_THRESHOLD > 0 &&

        selectCnt >= SELECTOR_AUTO_REBUILD_THRESHOLD) {

    selector = selectRebuildSelector(selectCnt);

    selectCnt = 1;

    break;

}
```
# 粘包/拆包问题    
TCP协议的消息是流式传输的，这意味着我们无法很好的分辨哪些数据组成一条完整的消息。一个TCP数据包会包含MAC首部，IP首部，TCP首部，数据，后三者称为MTU，是链路层一次能传输数据的大小，一般是1500字节。数据部分称为MSS，是一次TCP能传输的报文大小。此外TCP传输数据还受到滑动窗口，linux nagle算法等影响，导致无法简单确定一个完整的消息内容。    
例如要发送的消息是：abcdef，可能由于报文过长，进而进行拆包，变成两次TCP传输，分别是abc def。  
例如要发送的3条消息是：a b c，由于每次只发生一个字符，而每次都需要加上各种首部信息，是极大的浪费，所以会粘包合并为abc一次性发送。nagle算法是一种TCP/IP拥塞控制方法，主要用于解决频繁传递小数据包带来的网络拥塞问题，它的做法是将多个小数据包合并成一个发送，这种思路和我们平时批量处理问题，kafka批量发送消息的思想是一样的，但这会有延迟问题。      
粘包/拆包的本质问题是为了在保证传输性能的前提下，可以识别出一个完整的信息。解决方式是在**应用层定义传输协议**，常见的做法有：  

- **消息长度固定(FixedLengthFrameDecoder)**
如，发送端固定每次发送64字节，接收端每次接收到64字节就认为是一个完整的消息。    
消息长度固定的缺点显而易见，固定的长度太长会浪费空间，太短可能会丢失数据。  

- **换行分隔符和自定义分隔符(LineBasedFrameDecoder，DelimiterBasedFrameDecoder)**
如，识别到换行符就认为是一个完整的消息，redis客户端/服务端的resp协议就是使用换行分隔符作为标识。  

- **消息长度 + 消息内容(LengthFieldBasedFrameDecoder)**     
如，每次发送都通过一个字段告知服务端本次消息的长度，当接收到这个长度时才认为是一次完整的消息，如果一次接收后还达不到这个长度，那么需要继续解析后面的数据包。     
如，6 abcdef，被拆包了，服务端接收到的是：6 abcd，那么需要继续解析下一个数据包得到ef。   
这种方式用得最多，就好像http请求头一样，还可以添加更多需要的字段标识。   
netty针对以上做法都提供了实现，通过相应的ChannelHandler即可支持。   
linux默认是开启nagle算法的，netty为了消息的实时性，默认禁用了nagle算法。   

# netty内存管理机制
[参考这篇文章](https://zhuanlan.zhihu.com/p/259819465)，netty的内存管理(分配，回收)参考了jemalloc的设计，整体上看比较复杂。   

**PoolChunck**，netty像操作系统申请内存最小单位，默认为16kb。   

**Page**，PoolChunck管理的最小单位，为8kb。（os的Page是4kb）    
一个PoolChunck可以分为2048个Page，这些Page可以组成一颗高度为11的二叉树，树的节点就是Page。  
这么做的目的是可以快速找到可以分配内存的位置（Page），当要分配的内存不足2的n次幂时，netty会向上取整，例如申请19k，会申请32k。    

**PoolChunckList**管理着一组PoolChunck，通过链表连接起来。    
多个PoolChunckList又会通过链表连接，并且netty会动态更改它们的位置，使得分配率低的在前面，这样做的目的是可以高效分配，保证使用率。     

**PoolSubpage**，对于小于一个Page大小的通过PoolSubpage管理，它内部又分为tiny和small两种，每种又分为多种规格，tiny从16B开始，以16的倍数，一直到496B，31种规格，如16,32,48,64...4096。small分为512B,1024,2048,4096 4种规格，一个Page会以一种规格进行细分，例如4096B规格的，一个Page可以分成2部分。   
当需要小内存时，netty会申请一个Page，然后对Page进行划分，然后分配。   

**PoolArena**，内存管理者，内部有一个由链表组成的PoolChunckList，一个tiny PoolSubpage，一个small PoolSubpage，内存申请和回收就是通过PoolArea完成的。netty根据cpu核数创建PoolArena，线程申请内存就是在不同的PoolArena进行，减少多线程并发申请的竞争。   

**PoolThreadCache**，线程内存缓存，netty内存使用后不会立刻归还，而是通过PoolTheradCache进行缓存。申请时也是先看线程内缓存有没有合适的内存，有则直接使用，没有再像PoolArena申请。线程内缓存的好处是没有多线程竞争，分配效率特别高，这一点和jvm的TLAB思想是一致的。    

# netty中的高性能设计    
## FastThreadLocal   
jdk提供的ThreadLocal，内部是通过ThreadLocalMap来存储线程的本地数据，ThreadLocalMap内部维护了一个Entry数组(默认大小16)，就过hash找到数组的位置。ThreadLocalMap的key就是ThreadLocal对象，value是要存储的值，通过拉链法解决hash冲突，当数量超过数组大小时，会进行扩容，扩容就是创建2倍大小的数组，然后通过rehash找到数组新的位置。    
从上可以看出，ThreadLocal的性能在于当出现hash冲突时，使用拉链法查找时间复杂度会为O(n)，另外扩容时需要通过rehash也会比较耗时。     
netty提供了FastThreadLocal，搭配FastThreadLocalThread使用，FastThreadLocalThread内部维护了一个InternalThreadLocalMap，维护了一个默认大小为32的数组。  
每次创建一个FastThreadLocal会分配一个index，这个index是全局维护的AtomicInteger。比如：
```
FastThreadLocal fastThreadLocal1 = new FastThreadLocal();   //index为1
FastThreadLocal fastThreadLocal2 = new FastThreadLocal();   //index为2
FastThreadLocal fastThreadLocal3 = new FastThreadLocal();   //index为3
```
fastThreadLocal1的线程数据都会放在FastThreadLocalThread关联的InternalThreadLocalMap内部数组下标为1的位置，fastThreadLocal2的线程数据都会放在FastThreadLocalThread关联的InternalThreadLocalMap内部数组下标为2的位置（0位置是一个Set，存储所有FastThreadLocal对象）。    
从上可以看出，相比jdk的ThreadLocal，FastThreadLocal的高性能在于：   
1、查找数据时，FastThreadLocal可以通过index快速定位到数组位置，没有hash冲突，查找速度为O(1)。    
2、当需要扩容时，按index以2的n次幂创建一个新的数组，然后把数据搬过去，不需要rehash。   

## 时间轮 HashedWheelTimer   
jdk提供的Timer,DelayedQueue,ScheduledThreadPoolExecutor都提供了定时功能，但这些定时任务添加和取消任务时间复杂度都是O(logn)，并且在实现上都有一些不足。例如Timer,这是最早期的定时器，它内部维护了一个小根堆数据结构，最先要执行的任务会放到堆的根，然后开启一个TimerThread去判断根节点的任务是否可以执行，如果可以执行就取出，然后执行，否则继续等待。Timer的问题是它是单线程的，当有任务执行比较久时，后面的任务都会阻塞，另外它没有进行异常处理，如果有任务执行异常，后面的任务都无法执行。   

为了提升定时任务的添加删除效率，netty提供了基于时间轮算法的HashedWheelTimer。     
时间轮算法与时钟一样，是一个环形结构，根据时间跨度将环形划成多个刻度，任务就是放在每一个刻度内（netty中每个刻度是一个HashedWheelBucket对象），同一个刻度内的任务通过链表相连。时间轮的做法就是每次推进一个刻度，然后执行这个Bucket内的所有任务。当定时任务等待的时间超过一个轮次的时间时，会通过轮次(round)表示这个任务是第几轮要执行的，例如划分了12个刻度，每个表示1s，13后的任务会放在第一个位置，然后round为2。    

netty的HashedWheelTimer实现了Timer(netty中的)，表示一个定时任务管理器，此外还有TimerTask接口表示一个任务，Timeout接口关联Timer和TimerTask，可以判断任务是否到期和取消任务。    
HashedWheelTimer在redission的重试中应用。     

HashedWheelTimer主要缺点是：   
1、空推进问题，例如第一个任务是1s后执行，第二个任务是1小时后执行，那么线程就会不断的sleep和唤醒推进，但是一直没有任务执行。       
2、执行任务时，链表遍历会包含各个层级的round，以及round的维护(每次执行完round都-1)，会影响性能。     
3、单线程，如果有任务执行耗时久，会影响后面的任务。    

**kafka时间轮**    
kafka也用了时间轮算法处理定时任务，与netty不同的时，kafka使用了DelayedQueue来保存bucket，每次取根bucket，如果没到执行时间，就会一直阻塞，不会唤醒，这样就不会有空推进问题。       
另外kafka使用了层级时间轮，超过一轮时间的任务会存储在第二层时间轮里，第二层时间轮的一个刻度是第一层时间轮的总跨度，同理第三层的每一个刻度是第二层时间轮的总跨度。例如第一层时间轮每一个刻度是20ms，总共有10个刻度，总跨度就是200ms，那么第二层每个刻度就是200ms，总2000ms。    
第一层时间轮每执行完一轮，就会触发其它层级的任务降级，随着时间的推移，所有任务都会降级到一层时间轮里执行，这样就不需要维护轮次，遍历也更加高效。     

## Mpsc Queue  
[JCTools](https://github.com/JCTools/JCTools) 是java一个第三方并发工具包，提供一些高性能的并发数据结构。    
在jdk中队列是实现了Queue接口的集合，主要有阻塞和非阻塞两大类。阻塞队列通过加锁保证线程安全，如ArrayBlockingQueue，LinkedBlockingQueue，PriorityBlockingQueue，DelayQueue等，非阻塞队列通过CAS保证线程安全，如ConcurrentLinkedQueue，ConcurrentLinkedDeque。    
jdk队列的问题在于，要么是加锁的实现，高并发下会影响效率。不加锁的使用链表，在数据量大时，对GC不够友好。且存在伪共享问题。    

**伪共享**    
由于cpu的速度与内存的读取速度是不成正比的，通常会有几个数量级的差别，为了提升效率，cpu会设计多级缓存，提升cpu的读写效率。分别为一二三级缓存，越靠近cpu的缓存读取效率越高，容量越小，cpu在读取内存数据时，会读取一个缓存行大小的数据(通常是64byte)，然后进行缓存，对数据进行修改也会先改缓存的，如果数据被其它线程读取，那么cpu就需要将数据同步回主存，然后其它线程再加载到cpu缓存进行操作，这个过程是通过cpu MESI协议完成的。     

伪共享的问题在于，一个缓存行包含了不同线程的数据，当其它线程频繁修改数据时，当前线程不得不频繁的从主存重新加载数据。    

伪共享的解决方式很简单，就是使用字段填充，使变量独自占用一个缓存行。例如：  
```
long p1,p2,p3,p4,p5,p6,p7;
long value;
long pa,pb,pc,pd,pe,pf,pg;
```
这么写无论cpu如何加载数据，value都是和填充字段占用一个缓存行的，不会和别的线程共享一个缓存行。需要注意的是缓存行的大小和cpu架构有关，64位操作系统下主流的都是64字节，也有128字节的。jdk8提供了@Contended注解可以用来解决伪共享问题，它默认就是使用128字节填充，因为这样可以覆盖64字节的场景，但也可以通过jvm参数设置大小。    

JCTools并发工具通过字段填充解决了伪共享问题，Mpsc4个字母分别代表：multiple，producer，sigle，consumer，也就是多个多生产者一个消费者的场景，生产者会有多线程并发问题，生产者只有一个线程没有并发问题，Mpsc使用CAS代替加锁，内部使用了环形数组代替链表。    
JCTools在netty,rxjava,caffeine都有应用，与之类似的工具还有Disruptor。   

## 对象池
[参考这篇文章](https://www.cnblogs.com/binlovetech/p/16451821.html)，
对象池是池化思想的应用，用完不弃，缓存复用。一般用在一些频繁创建，创建成本高的对象上，例如数据库连接池。     

在netty中，考虑到高并发，且每次IO操作都会产生大量的对象，如果每次都创建，涉及到JVM的类加载机制，内存分配，对象初始化，耗时会增加，同时对象用完后废弃，过多的垃圾对象会影响GC。所以netty对一些频繁创建的对象做了池化，例如PooledHeapByteBuf ，PooledDirectByteBuf，ChannelOutboudBuffer里的Entry对象，这些池化对象内部都有一个**Recycler**的成员变量，对象创建完后会回收，缓存起来。     

具体的思路是：netty的对象池为了提高对象的分配回收效率，设计为无锁状态，对象的获取和回收都是不需要加锁的。Recycler内部会维护一个**Stack**数据结构，本线程回收的对象会缓存到这个栈中，需要时就是从栈顶获取。

还有一个**WeakOrderQueue**链表数据结构，用于保留其它线程回收的对象。什么意思呢，本线程(A)创建的对象可能会被其它线程使用，如B,C，如果其它线程用完回收也放到Stack，就会和本线程的回收和获取产生线程冲突，就需要加锁会影响并发效率，所以netty为其它每个线程设计一个WeakOrderQueue，多个线程的WeakOrderQueue通过链表关联。每一个WeakOrderQueue内部又维护一个链表，每个链表元素称为一个Link，每个Link都包含读写索引，和一个长度为16的数组，存储的和Stack存储的一样都是DefaultHandler（可以用来创建源对象的对象）。    
这样每个其它线程回收，就只会放到它自己的WeakOrderQueue，没有多线程的竞争，不需要加锁。   

当需要创建对象时，对象池会先从本线程的Stack获取，如果获取不到，就通过链表找WeakOrderQueue，找到一个可以用的Link，将其元素都迁移到Stack，每次最多迁移一个Link，16个对象，如果找不到就new创建一个。      
整个Recycle的设计还是非常复杂的，新版本的netty对其进行了重构。    

## 零拷贝   
以从磁盘读取一个文件，通过网络发送出去为例。   
1、调用系统读取api，从用户态切换到内核态，发生第一次用户内核态切换。   
2、系统从磁盘加载文件到内核缓冲器，发生第一次拷贝，但这一次拷贝属于DMA，不需要CPU处理。  
3、从内核缓存区拷贝数据到用户内存，发生第二次态切换，第二次拷贝，这一次拷贝需要cpu处理。  
4、调用api发送数据，从用户态切换到内核态，数据从用户空间拷贝被socket缓冲区，发生第三次态切换和第三次拷贝，这一次拷贝仍然需要cpu处理。  
5、将socket缓存区拷贝数据拷贝到网卡缓存区，发送。发生第四次拷贝，这一次拷贝不需要cpu处理。
6、返回结果给应用程序，发生第四次态切换。    

可以看到传统方式读取发送一次数据需要4次用户内核态切换和4次拷贝，其中2次属于DMA拷贝，不需要cpu处理，2次需要。    

linux sendfile优化，对于一些不需要用户应用程序处理的数据，从内核态缓冲区拷贝到用户空间，再从用户空间拷贝到socket缓冲区，这两步可以合并为一步，直接从内核缓存区拷贝到socket缓冲区，这样就节省了一次cpu拷贝和两次态切换。   
在linux2.4后对其进行优化，从内核缓冲区拷贝到socket缓冲区改成只拷贝一些内存描述信息，这样的拷贝几乎可以忽略不计，节省了一次cpu拷贝，所以整个过程不再需要cpu拷贝，称为零拷贝。    

netty中的FileRegion底层用了java nio的FileChannel的transferTo实现零拷贝，底层调用的就是sendfile方法。    

除此之外，netty还实现了许多**应用层面的零拷贝**，如：  

- **堆外内存**  
如果使用堆内内存，实际还有一次从堆内内存拷贝到堆外内存的过程，因为堆内内存受jvm管理，受gc影响，地址可能会改变，所以需要先拷贝到堆外内存，netty可以直接使用堆外内存较少这次拷贝。  
- **CompositeByteBuf**  
CompositeByteBuf可以组合多个ByteBuf，内部通过数组保留原ByteBuf的引用，所以不需要移动底层的字节数组，通过索引，可以通过CompositeByteBuf直接操作ByteBuf。    
- **Unpooled.wrappedBuffe**   
将一个字节数组包装成ByteBuf，使用的仍然字节数组引用，没有内存拷贝。       
- **ByteBuf.slice**   
可以将一个ByteBuf拆分成多个，底层仍共享一个字节数组，也不需要内存拷贝。   

## Sharable Handler
在往Pipeline添加handler的时候，通常直接在initChannel()使用new Handler()，也就是每个请求都会创建一个handler对象，在请求量大的时候，会有性能销毁。
如果是先new一个handler，再在initChannel()传入实例对象，那么会报错，因为netty认为这个handler不是线程安全的。
netty提供了@Sharable注解可以标识Handler，表示该Handler是线程安全的。那么就可以new一个handler添加到Pipeline，这样不会报错，所有请求只会公用一个Handler对象。    
```
AHandler ahandler = new AHandler();
ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap.group(boss, worker)
    .channel(NioServerSocketChannel.class)
    .childHandler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel socketChannel) throws Exception {
            //如果AHandler没有打@Sharable注解会报错
            socketChannel.pipeline().addLast(ahandler); 
        }
    })
.childOption(ChannelOption.SO_KEEPALIVE, true);
```

# 心跳机制    
首先来了解一下TCP协议的keep_alive机制，连接保活，指的是客户端与服务端连接建立后，不会马上断开，如果双方一直没有数据交互，TCP连接会一直占用资源。TCP协议默认在没有收到数据会在2h后，以每75s的频率，向客户端发送消息，如果发送9次还没有收到回复，则认为客户端下线了，服务端会关闭这个TCP连接。相关参数  
```
$ sysctl -a| grep tcp_keepalive
net.ipv4.tcp_keepalive_intvl = 75
net.ipv4.tcp_keepalive_probes = 9
net.ipv4.tcp_keepalive_time = 7200
```
1、TCP协议是TCP协议层的实现，也就是操作系统层面，默认是关闭的，在应用层面看来不够灵活。  
2、且探活时间2h非常久，可能连接还是活跃的，还服务已经不可用。    
3、TCP协议才支持，如果缓存UDP协议就不支持了。    

所以通常还需要应用层面的心跳机制，netty中使用**IdleStateHandler**实现心跳机制。  
```
public IdleStateHandler(int readerIdleTimeSeconds, int writerIdleTimeSeconds, int allIdleTimeSeconds) {}
```
IdleStateHandler本身也是一种Handler，所以使用时需要添加到Pipeline，它是一种DuplexHandler，支持入站和出站，所以可以同时处理读写消息。    
IdleStateHandler的原理，例如客户端发送心跳到服务端，两者都需要添加一个IdleStateHandler。   
客户端的IdleStateHandler设置writerIdleTimeSeconds，表示多少秒后没有写数据就触发事件。  
具体是IdleStateHandler创建一个定时任务添加到**EventLoop的TaskQueue**，EventLoop会把它拿出来执行。这个任务的逻辑是，判断最后一次写的时间距离当前时间是否超过指定时间(writerIdleTimeSeconds)，如果是，则触发一个WRITE_IDLE事件，并交给下一个Handler处理。无论有没有超时，都会重新创建一个定时任务，再次丢到TaskQueue，下次继续检查。      
下一个Handler需要我们自己编写逻辑，重写userEventTriggered方法，捕获WRITE_IDLE事件，然后发送请求到服务端。     
服务端的IdleStateHandler设置readerIdleTimeSeconds，表示多少秒后没有读数据就触发事件。  
逻辑和客户端的应用，只不过这里触发的是READ_IDLE事件，也由自定义的Handler捕获处理，例如关闭Channel。    

# 堆外内存    
**Cleaner回收机制**    
Cleaner继承了PhantomReference，最终继承了Reference。    
PhantomReference虚引用，它的作用是跟踪gc，需要和一个队列ReferenceQueue绑定使用，当一个对象是虚引用时，第一次gc后就会把它放到队列里面。    
Reference在初始化的时候会启动一个ReferenceHandler线程，用于处理虚引用。    
ReferenceHandler在判断是Cleaner对象的时候，就会执行它的clean方法，clean方法可以创建创建Cleaner时传进来的Runnable方法。     
在java中分配堆外内存可以使用ByteBuffer.allocateDirect()，它内部会创建一个DirectByteBuffer对象，DirectByteBuffer会使用unsafe分配堆外内存，然后创建一个Cleaner对象，在Runnable方法内部再使用unsafe释放内存，这个Runnable就是在Cleaner对象被回收的时候调用，也就是在DirectByteBuffer对象回收的时候，就会回收堆外内存。     
这样看起来堆外内存也会随着堆内引用回收而回收，但不好的是，对象的回收时间是不确定的，例如对象可能晋升到老年代，而old gc迟迟不发生，那么堆外内存就一直无法回收。  
与之相关的jcm参数是-XX:MaxDirectMemorySize可以设置最大堆外内存大小，当不足以分配时，就会触发OOM。  

netty的内存主要分为池化的堆内存，池化的堆外内存，非池化的堆内存，非池化的堆外内存。其中非池化的堆内存由jvm管理，不需要考虑内存泄漏问题，其它3种均可能存在泄漏。   
ByteBuf继承了ReferenceCounted接口，该接口主要有几个方法，refCnt表示引用计数，retain引用计数+1，release引用计数-1。当refCnt不为0，且没被引用着时，就表示内存泄漏。   

具体做法是通过java虚引用实现的，netty会为ByteBuf创建一个虚引用，当ByteBuf不再使用时，发生gc后，就会加入虚引用关联的队列。源码位置：io.netty.util.ResourceLeakDetector.DefaultResourceLeak，netty会将所有对象保存在一个allLeaks集合当中，当有手动调用ByteBuf.release时，会调用DefaultResourceLeak.close方法，从allLeaks删除。那么在对象回收加入回收队列时，会遍历也调用close方法，如果返回false表示已经释放过了，没有内存泄漏问题。否则如果从集合删除成功，则表示没有调用过release方法，存在内存泄漏。        

netty可以设置**leakDetection.level**参数来检测内存泄漏，如下：    
```
-Dio.netty.leakDetection.level=paranoid
```
level有4种级别：  
1、disabled，关闭堆外内存泄漏检测；   
2、simple，以 1% 的采样率进行堆外内存泄漏检测，消耗资源较少，属于默认的检测级别；   
3、advanced，以 1% 的采样率进行堆外内存泄漏检测，并提供详细的内存泄漏报告；   
4、paranoid，追踪全部堆外内存的使用情况，并提供详细的内存泄漏报告，属于最高的检测级别，性能开销较大，常用于本地调试排查问题。  

Netty 会检测 ByteBuf 是否已经不可达且引用计数大于0，判定内存泄漏的位置并输出到日志中，你需要关注日志中**LEAK**关键字。    

# netty中的设计模式  

- **责任链模式**  
Pipeline中的ChannelHandlerContext构成一个双向链表，入站请求会从head节点开始执行到tail节点，出站会从tail节点开始执行到head节点，通过这种方式可以很方便的在Pipeline中加入自定义逻辑读取和响应请求。      

- **单例模式**   
netty提供了SelectStrategy表示nio select时的策略，默认的实现类DefaultSelectStrategy就是一个单例，它是通过饿汉模式实现的。SelectStrategy支持的策略有select，continue，当EventLoop队列中有任务要执行时，此时的策略是continue，不会调用select方法进行阻塞(实际是调用了selectNow立刻返回)，保证队列里的任务得到执行。如果队列里没有任务要执行，策略就是select，此时会调用select方法挂起线程。（计算一个阻塞时间，到时间无论select有没有事件都返回，执行队列任务）  

- **工厂模式**   
netty的ReflectiveChannelFactory继承了ChannelFactory，用于创建Channel。    ReflectiveChannelFactory会根据在BootStrap阶段设置的Channel类型，例如NioServerSocketChannel，通过反射创建对应的Channel对象。   

- **建造者模式**   
netty的BootStrap和ServerBootStrap使用的就是建造者模式，通过链式调用，设置不同的参数，进行引导配置。     

- **观察者模式**   
观察者模式可以通过观察，在感兴趣事件发生时，进行响应。netty的ChannelFuture继承了Future接口，表示异步结果。当我们调用writeAndFlush方法时返回的就是ChannelFuture，在数据flush写入socket后，ChannelFuture注册的listener就会触发调用。    

# netty最佳实践    

- **配置Boos EventLoopGroup和Worker EventLoopGroup**      
netty可以通过设置Boos EventLoopGroup和Worker EventLoopGroup 实现各种reactor模型，推荐同时配置两者，也就是使用主从多reactor模型，这样Boos负责接收连接建立，worker负责连接读写，分工明确，性能较佳。   

- **ChannelHandlerContext.writeAndFlush和Channel.writeAndFlush**     
ChannelHandlerContext会从当前Context开始往后传递，Channel会从tail节点开始往后传递，当不需要从tail节点开始传递时，使用ChannelHandlerContext.writeAndFlush可以缩短事件传播路径。   

- **@Sharable**    
使用Sharable注解修饰的Handler可以在所有请求公用一个Handler对象，避免创建过多的Handler对象。注意使用使用这种方式的Handler必须是线程安全的。   

- **使用业务线程池**     
netty使用少量的EventLoop线程管理成千上万的连接和处理请求，默认会开启cpu * 2个EventLoop线程。如果使用EventLoop进行耗时的业务操作，那么系统很快就会崩溃。所以建议自定义线程池或者使用netty提供的EventExecutorGroup线程池进行复杂的业务处理。

- **使用内存池/对象池**
内存池和对象池都是池化思想，通过复用减少频繁创建的开销。使用PooledByteBufferAllocator可以分配池化的堆内/堆外内存，使用堆外内存在IO的时候还可以减少一次堆内->堆外内存的拷贝。
使用Recyler可以创建对象池，对象用完后会缓存到线程绑定的Stack结构中，不会被GC回收。  

- **cpu亲和性**   
netty的目标是处理海量请求，对性能的要求非常高。
cpu亲和性，是指将线程和cpu绑定，称为“绑核”，这样可以更好利用cpu缓存。
可以使用[Affinity](https://github.com/OpenHFT/Java-Thread-Affinity)轻松集成。  

- **开启nagle**    
nagle算法是linux底层算法，用于合并多个较小的TCP数据包，提升传输效率。
但nagle算法会有一定的延迟性，对实时性要求较高的场景不适合，netty默认是关闭。
如果对实时性要求不高的场景，开启可以降低小数据包带来的网络拥塞问题。    
