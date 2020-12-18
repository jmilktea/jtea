## 基础   
出于安全考虑，操作系统把空间分为内核空间和用户空间两部分，内核空间拥有操作系统的所有权限（包括对磁盘，网络的读写操作），而用户空间只拥有用户相关权限，一般需要经过内核空间才能获取相关操作权限。    
例如，应用需要读取磁盘文件，拥有进程就需要发起系统调用，通知内核加载文件，内核将文件内容加载到内核空间后，再复制到用户空间，此时应用进程才能进行读取。  
用户空间和内核空间都有一块内存缓冲区域，用于存放数据，这里我们暂时称为app buffer和kernel buffer。

- 两个核心阶段  
数据被加载到内核空间的过程我们称之为**数据准备**阶段，数据由内核空间拷贝到用户空间的过程我们称之为**数据复制**阶段。    
数据准备阶段，是由内核将数据从硬件设备加载到内核空间，现在的硬件设备基本都支持[DMA](https://baike.baidu.com/item/DMA/2385376?fr=aladdin)技术，
也就是这个数据拷贝过程是不需要cpu参与的，而是利用硬件上的芯片进行控制（可以看做硬件上有个小型cpu），这样可以节省消耗机器的cpu。  
而数据复制阶段是在操作系统内部完成的，这个过程需要消耗cpu资源。
总结：**数据由硬件设备拷贝到内核空间，不需要消耗cpu资源；由内核空间拷贝到用户空间需要消耗cpu资源。**

我们以应用接收到一个请求，在数据库读取数据返回为例，整个过程如下：  
![image](https://github.com/jmilktea/jmilktea/blob/master/linux/images/linux-io.png)  
1. 客户端发起请求，由服务器网络设备接收到该请求  
2. 收到请求后，将请求数据拷贝到内核空间，这个过程利用了网络设备的DMA，不会消耗cpu资源
3. 接着再从内核空间复制到用户空间，这个过程是在系统内部完成，会消耗cpu资源  
4. 应用程序对用户空间数据进行读取
5. 应用程序发起系统调用，加载请求需要的数据 
6. 从数据库读取数据，将数据拷贝到内核空间，这个过程利用的硬盘设备的DMA，不会消耗cpu资源  
7. 接着再从内核空间复制到用户空间，这个过程也是在系统内部完成，会消耗cpu资源
8. 应用程序在用户空间对数据进行加工  
9. 将数据从内核空间拷贝到输出设备,这个过程也是利用了网络设备的DMA，不会消耗cpu资源
10. 返回数据  

- 零复制技术(zero copy)  
从上面流程可以看到，数据需要经过kernel buffer->app buffer->send buffer，这个过程都需要cpu的参与。但如果是进程不需要对数据进行任何处理，也就是可以不需要复制到app buffer，这就是零复制。
零复制用于减少内核和用户空间的切换次数，节约cpu资源。[零复制](https://baike.baidu.com/item/%E9%9B%B6%E5%A4%8D%E5%88%B6/22742547?fr=aladdin) 


## 五种I/O模型   
**所谓的I/O模型描述的是数据准备和数据复制阶段，进程/线程所表现状态。**  

1. Blocking I/O模型   
![image](https://github.com/jmilktea/jmilktea/blob/master/linux/images/blocking-io.png)   
阻塞io模型从进程发起read读取数据开始，到数据加载到用户空间可以被读取，整个过程都是阻塞的。  
该模型下，发起read操作后就会被挂起，相当于进入休眠状态，进程不会获得cpu资源，不能做其它事情。当数据复制好，才返回结果给进程，这时才解除block状态，进程开始读取数据。
上面说到的，数据准备是不消耗cpu资源的，这个过程cpu可以处理其它任务。

2. Non-Blocking I/O模型   
![image](https://github.com/jmilktea/jmilktea/blob/master/linux/images/non-blocking-io.png)   
非阻塞io模型进程发起read读取数据，系统会返回一个EWOULDBLOCK标志，并立即返回。所以在数据准备阶段，进程不会交出cpu，会一致占用cpu资源，此时能利用cpu做一些事情。但需要不断的去轮询数据是否准备好，可以理解为用一个while循环去轮询，这样也是很消耗cpu资源的。另外由于轮询有一个时间间隔，会影响响应的实时性。  
在数据复制阶段，cpu资源会被内核抢占，所以此时进程依然被阻塞的。

3. Multiplexing I/O模型  
![image](https://github.com/jmilktea/jmilktea/blob/master/linux/images/multipliexing-io.png)  
io多路复用模型，是指可以检查多个io的等待状态。有三种io复用模型：select/poll/epoll(windows没有epoll，是iocp)，实际它们都是内核级别函数，用于监控指定的描述符是否准备就绪，这里不讨论select/poll/epoll的区别。
在Non-Blocking I/O模型下，需要由用户进程不断去轮询数据是否准备好，如果有很多个这种操作，就会有很多轮询，效率就会非常低。而多路复用的思想就是把这些轮询的操作交由内核去完成，而内核也不会一个个去检测，而是每次检查一批，只要有一个准备好了，就通知用户进程处理。以select为例，它可以最多监听1024个文件描述符。
需要注意的是，这里进程也是被select所阻塞的，看起来和blocking模型没什么区别，实际效率可能还更差一点，因为blocking只需要进行一次read调用，而这里需要调用select和read两次内核调用。
实际上再处理少量任务的情况下，它比blocking + 多线程的方式并没有什么优势，但在连接数高的场景下多路复用模型会有明显的优势，底层的select/poll/epoll可以用更少的线程处理更多的任务，例如可以用一个线程监听多个socket连接，而blocking每个socket都需要一个线程去监听。所以在高并发的场景下，io多路复用的优势就会提现出来，它可以用更少线程监听和处理更多的请求。

4. Signal-Driven I/O模型  
![image](https://github.com/jmilktea/jmilktea/blob/master/linux/images/signal-driven-io.png)  
基于信号模型的做法是通过调用系统函数注册一个信号，此时会立即返回，这个阶段进程不会被阻塞。当数据准备好，会收到一个SIGIO信号表示可以开始读取数据。
基于信号模型有一些限制，需要对应的文件描述符能支持，例如普通的文件IO就无法支持。
在数据复制阶段，cpu资源会被内核抢占，所以此时进程依然被阻塞的。

5. Asynchronous I/O模型  
![image](https://github.com/jmilktea/jmilktea/blob/master/linux/images/asynchronous-io.png)  
前面4种模型进程都有一个阻塞阶段，也就是在数据复制阶段进程都是阻塞的。
而异步io模型整个阶段进程都是非阻塞的，此时进程使用的是aio_read异步函数，然后系统立即返回。直到数据准备完成，也就是已经复制到app buffer了，系统会发一个信号，进程直接读取数据。
相比信号模型，异步IO不再乎是什么文件描述符，普通的文件也可以支持。
但异步io模型也不是没有缺点，首先它实现起来比较复杂，需要各种异步函数库的支持。另外，数据准备阶段是需要消耗cpu资源的，虽然此时进程不会被阻塞，但是此阶段会抢夺cpu资源，如果处理不当，可能会导致cpu资源都会系统抢占用于数据复制，而用户进程没有cpu可以使用。 

- 总结  
![image](https://github.com/jmilktea/jmilktea/blob/master/linux/images/io-compary.png)  
可以看到前4种模型，在数据复制阶段进程都是阻塞的，所以它们都是同步I/O模型。只有最后一种是真正的异步I/O，因为整个过程进程都不会被阻塞。






























