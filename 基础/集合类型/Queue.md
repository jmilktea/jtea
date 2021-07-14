本篇主要介绍下集合体系下Queue的继承体系和相关类的作用。  
Queue是一种FIFO类型的数据结构，在实际应用中也非常广泛，java中对此提供了丰富的支持。  
整个继承体系如下，图中虚线表示实现，实现表示继承。  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%9F%BA%E7%A1%80/images/Queue.png)   

Queue也是集合的一种，所以它也继承了集合的根接口Collection。  
**Queue**接口定义了队列的基本操作，如添加、删除、获取元素等。  
**BlockingQueue**定义了阻塞队列接口，阻塞队列顾名思义在获取元素时，如果队列没有元素需要进行等待，直到等到有元素能获取到才返回，在添加元素时，如果队列没有空间也进行等待，直到队列有空间能添加进去。阻塞队列有如下几个实现类：  
&emsp;**SynchronousQueue**：Synchronous本身就是同步的意思，SynchronousQueue内部并没有容器存储元素，当添加一个元素后，如果没有消费者对其进行获取，那么生产线程就会阻塞。当元素被获取后，生产线程恢复。     
&emsp;**PriorityBlockingQueue**：是一个无界，具有优先级的队列。添加到PriorityBlockingQueue的元素必须实现Comparable接口，也就是可以比较，每天添加元素时，它都会进行比较，确保每次获取时都是排序后的第一个元素。    
&emsp;**LinkedBlockingQueue**：是一个基于链表的有界队列。  
&emsp;**ArrayBlockingQueue**：是一个基于数组的有界队列。  
&emsp;**DelayQueue**：是一个具有延迟效果的无界队列，即只有有效期到期的元素才能被获取到，它也是一个无界队列，因为它的内部是基于PriorityBlockingQueue实现的。  
&emsp;**TransferQueue**：定义了一种生产者可以等待消费者的队列，它有一个**LinkedTransferQueue**实现类。与SynchronousQueue类似，但是它内部是没有容器可以存储元素的，TransferQueue可以看做是SynchronousQueue和LinkedBlockQueue的结合，既可以阻塞生产者又可以存储元素。  

**AbstractQueue**是一个抽象类，比较简单，就是将各种实现类可以复用的逻辑抽取出来。  

**Deque**的全名是Double enable Queue，也就是双端队列。它可以从头/尾添加和获取元素，由于两端都可以进出元素，所以它可以代替栈(Stack)。  
&emsp;**BlockingDeque**是阻塞型的双端队列，它继承了BlockingQueue接口。LinkedBlockingDeque是基于链表实现的有界的，阻塞型的双端队列。  
&emsp;**ArrayDeque**：是基于数组的有界双端队列。   
&emsp;**ConcurrentLinkedDeque**：在concurrent包下，是线程安全的基于链表实现的双端队列。  
&emsp;**LinkedList**：这个我们很熟悉，它也实现了Deque接口，也是一个双端队列。我们经常说ArrayList与它的区别，这也是一个点，ArrayList只实现了List接口，面试要是能说出这一点可以加分。  

**ConcurrentLinkedQueue**：在concurrent包下，是线程安全的Queue

