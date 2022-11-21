本篇我们来聊聊happend before原则，这是一个平时容易忽略的重要基础知识，面试也会经常被问到。       
happend before是先行发生的意思，A happend before B，表示A先行发生于B，A在B之前发生，A执行的结果对B可见。   
happend before原则就是规定代码执行顺序，符合这个原则的代码就会通过某些机制确保代码执行顺序和代码之间的可见性。    

**happend before解决什么问题**    
happend before主要解决两个问题：顺序性和可见性。一个是避免指令重排序，一个是多线程之间共享数据可见性问题，接下来我们具体说说这两个问题。    

**代码执行顺序还需要通过某些机制来确保？** 例如我们平时写：
```
int i = 1; //1
int j = 2; //2
```
难道实际的执行顺序会是 2->1 吗？答案是：是的，这个就是指令重排序。我们写的代码，实际都是翻译成具体的指令由cpu执行，为了保证程序高效的运行，就需要对指令进行一些优化，如更好的利用寄存器内存。这个工作是由jvm在底层完成的，例如我们在单线程下加锁，这个是完全没有必要的，jvm在底层就会偷偷把锁消除掉，这样执行起来更快。指令重排也可能是为了更好利用cpu内存，重排后的指令可以减少内存的重复申请，例如申请了一块内存给A，然后执行一段执行后内存不足被淘汰，后面又重新申请一块内存给A，重排后的指令可能就是申请后就一直执行，直到内存可以真正被回收。     
首先要确定的是，指令重排的初衷是好的，并且在绝大数时候会得到正面收益，因为jvm也是经过分析后重排序能带来收益才进行的（这点你可以理解为mysql执行计划，大多数时候mysql会选择一个好的执行计划，并不需要我们干预）但这种重排序有时候稍微不注意也可能会出现bug(如mysql选错执行计划了)，并且难以发现。    

指令重排在多线程下可能会出现非预期结果，如网上的一段代码，1,2这两句代码并没有什么关系，也没有要求happend before，所以有可能发生指令重排，一旦发生输出的就不是5了。    
```
public class Reordering {

    private static boolean flag;
    private static int num;

    public static void main(String[] args) {
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!flag) {
                    Thread.yield();
                }

                System.out.println(num);
            }
        }, "t1");
        t1.start();
        num = 5;                ① 
        flag = true;        ② 
    }
}
```

另一个问题是多线程共享数据可见性问题，这涉及到java内存模型（JMM）。为了屏蔽各种意见和操作系统的差异，达到跨平台的效果，java语言规范定义了JMM来统一内存的管理，JMM定义了主内存和工作内存，所有数据都会存放在主内存中，所有线程都可以访问，而各个线程有自己的工作内存，存放了主内存数据的拷贝，线程间互不干扰，主内存和工作内存间的数据同步由JMM负责实现。需要注意的是这里的工作内存不是直接对应到物理内存，那样就和主内存一样了，没必要在线程内再搞个工作内存，**工作内存是JMM的抽像概念**，并不是一块具体的物理内存，所以也没有jvm参数控制它的大小，实际工作内存可以是对应到cpu高速缓存，相比主内存离cpu更近，读写速度更快。    
如图：   
![image](https://github.com/jmilktea/jtea/blob/master/%E5%9F%BA%E7%A1%80/images/hb-1.png)     

JMM的这种设计，可能会导致多线程间数据的可见性问题，例如线程A对共享变量做了修改，实际是改了本线程工作内存的数据，还没有同步到主内存，其它线程读取到的也是工作内存旧的数据。   
最常见的就是并发编程中，共享变量都会用volatile关键字进行修饰来解决这个问题，如AtomicInteger的value属性    
```
public class AtomicInteger extends Number implements java.io.Serializable {
    private volatile int value;
}    
```

**happend before通过以下规则来规定顺序性和可见性问题，如下：**    
- 程序次序规则：同一线程内代码执行结果是有序的，即使发生指令重排，程序执行结果和顺序执行结果是一致的。注意是同一线程，就算指令重排也不能影响最终的结果。例如：  
```
int i = 1; //1
i = 0; //2
int j = i + 1;  //3
```
程序的执行结果j=1，这点不能改变，所以1,2两步不能改变顺序，否则就乱了。   
- 管程锁定规则：对一个锁的解锁操作happend before 后续对这个锁的加锁操作。这点很好理解，解锁了这个动作发生后是能被感知到了，否则后面的线程怎么加锁。    
- volatile变量规则：对volatile修饰的变量的更新操作，happend before于后面对这个变量的访问，包括读写操作。   
- 传递性规则：A happend before B，B happend before C，则 A happend before C。
- 线程启动规则：主线程启动子线程的前的操作，对于子线程是可见的。   
- 线程终止规则：子线程结束后，操作结果对于主线程是可见的。
- 线程中断规则：对线程 interrupt 方法的调用 happens-before 被中断线程代码检测到中断事件。    
- 对象终结规则：一个对象的构造函数执行的结束 happens-before 它的 finalize()方法。    

**volatile**   
volatile变量规则规定了对变量的更新操作，那么其它线程可以立刻看到最新的值，前面我们说到数据会存在线程的工作内存和主内存，那么它是怎么保证的呢？    
java底层在对volatile变量进行写操作后，会使用cpu lock前缀指令将数据写会主内存，保证可见性。同时会对volatile变量的读写加入内存屏障，保证顺序性。这里涉及到cpu缓存一致性和内存屏障问题，这里不深入分析，有兴趣可以学习下，附cpu缓存一致性协议：[MESI动图](https://www.scss.tcd.ie/Jeremy.Jones/VivioJS/caches/MESIHelp.htm)。   
![image](https://github.com/jmilktea/jtea/blob/master/%E5%9F%BA%E7%A1%80/images/hb-3.png)   

**单例-懒汉模式**    
```
    private static Singleton instance = null;

    private Singleton(){}

    public static Singleton getInstance() {
        if (instance == null) {  //1
            synchronized (lock) {
                if (instance == null) {
                    instance = new Singleton();  //关注这一行代码
                }
            }
        }
        return instance;
    }
```
懒汉模式的写法如上，使用加锁双重检查的方式，但仔细思考，会不会有指令重排的问题呢？      
在instance = new Signleton() 实例化的时候，底层实际执行几个步骤：    
1.分配内存空间   
2.实例化对象，属性初始化    
3.将分配的内存地址赋值给对象，此时对象不为null    

如果2,3发生指令重排，那么就是一个不为null，但是没有初始化好对象，此时另一个线程在第一个if判断不为null就返回，执行后面的逻辑，就可能出错了。    
解决方式就是要用到happend before中的volatile变量规则，instance用volatile修饰，按照volatile变量规则，对这个变量的修改会先发生于对这个变量的读取，所以能读取到一个完整的对象。    
上面这个这也是网上大部分的解释，但仍给我留下一个疑问。如果instance = new Signleton() 会发生这个问题，那么我们平时在普通方法new对象为什么不需要关注这个问题呢？   
假如我们只是单纯写一行Object obj = new Object()，后续没有对obj对象进行使用，不只可能会发生重排序，编译器甚至会完全去掉这一行无用的代码。后续obj有使用的话，按照上面8个原则中的第一个程序次序规则，new过程发生重排序，结果也是先行发生于对obj的使用，所以不会有问题。   

所以单例懒汉模式实例变量需要加volatile修饰，口说无凭，我们可以看下spring boot的源码ApplicationConversionService，这是spring boot的字段转换器，它就是一个单例，可以看到sharedInstance就是用volatile修饰的，源码如下：    
```
public class ApplicationConversionService extends FormattingConversionService {

	private static volatile ApplicationConversionService sharedInstance;

	public static ConversionService getSharedInstance() {
		ApplicationConversionService sharedInstance = ApplicationConversionService.sharedInstance;
		if (sharedInstance == null) {
			synchronized (ApplicationConversionService.class) {
				sharedInstance = ApplicationConversionService.sharedInstance;
				if (sharedInstance == null) {
					sharedInstance = new ApplicationConversionService();
					ApplicationConversionService.sharedInstance = sharedInstance;
				}
			}
		}
		return sharedInstance;
	}
```

**总结**    
happend before原则就是通过定义一些规则保证程序执行的有序性和可见性，只要我们的代码符合这些规则，就可以按照我们的预期得到结果，否则可能会有问题，特别是在多线程的场景下，具体有上面列举的8条规则。          
如果面试被问到，典型的可以举volatile变量规则，底层通过lock实现类似内存屏障的功能，确保多线程间共享变量的可见性，再通过单例懒汉模式可能出现的问题，结合spring boot ApplicationConversionService 源码分析，基本就可以了。    

