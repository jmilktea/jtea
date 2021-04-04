## CAS
这次我们来聊下并发编程里的cas（全称是compare and swap），它在java并发编程中出现的频率非常高，cas是一种自旋锁。
其原理是通过比较被修改的值和当前线程持有的值，如果相等才允许修改，如果不相等，则认为被其它线程修改过了，开始自旋，自旋就是更新自己的旧值，然后继续比较，直到相等，修改成功。如图：
![image]()  

我们知道多线程场景下对同个资源的操作会有并发问题，可能会出现不正确的结果或意向不到的错误，通常我们会通过对共享资源加锁来解决这个问题，保证同一时刻只有一个线程能访问共享资源。在java里synchroized关键字就可以实现加锁，保证对共享资源的安全访问。  

- 那么有了synchroized，为什么还需要cas呢？
首先在jdk1.6以前synchroized的性能比较差，其加锁过程比较简单，就是直接通知系统将没有获取锁的线程阻塞，这涉及到用户态和内核态的切换，影响性能。jdk1.6开始对synchrozied进行了许多优化，增加了锁升级的过程，从偏向锁->自旋锁->重量级锁。在竞争没那么强的情况下，不需要升级到重量级锁（也许永远不需要升级重量锁呢），性能有所提升。尽管如此，当synchroized升级到重量级锁后，就会一直使用，不会逆向，也就是由重量级锁再降级为自旋锁了，所以如果后续的并发没那么高，还是会一直使用重量级锁模式。可以看到synchroized锁升级过程中也使用到了自旋锁，实际就是cas，默认自旋次数是10次，可以通过jvm -XX:PerBockSpin参数进行配置。那么cas是不是就一定比synchrized性能好呢？答案显然是否定的，存在即有意义，cas的特点是会不断的进行循环判断，这会消耗cpu资源，对于并发大情况，自旋次数可能非常大，期间会一直占用cpu资源。另外cas只能保证单一资源的原子性，不能保证代码块的原子性，synchroized则可以对代码块，方法和类加锁。

- 乐观锁与悲观锁  
从思想上来说，cas属于乐观锁，synchroized属于悲观锁。乐观锁想法乐观，认为并发不大，只有在修改值时才加以判断，失败则可能重试一会就成功了。悲观锁想法悲观，认为并发较大，肯定会有线程和自己同时修改，所以一开始就先把锁上了，这样就可以安心做自己的事情了。
乐观锁和悲观锁我们在数据库也经常用到，如：
```
update t_order set status = 'complete' where id = 1 and status = 'init';
```
将订单状态修改为已完成，如果此时有另一个线程提前将订单状态改了，不是init了的话，那么修改就会失败。
```
select * from t_order where id = 1 for update
```
加的则是悲观锁，直接将id=1该行锁定，直到其提交，其它线程没有操作id=1这一行的机会。

## 源码  
jdk并发包java.util.concurrent.atomic里提供了许多简单的原子类操作，如AtomicInteger，AtomicBoolean，AtomicIntegerLong，其内部都是使用cas实现原子性操作，我们以AtomicInteger为例，它有两个重要的属性
```
    private volatile int value;
    private static final long valueOffset;
```
value就是要比较的值，它是通过volatile修饰的，保证可见性，也就是线程间修改可以立即被其它线程读取到。  
valueOffset顾名思义就是value的偏移地址，也就是内存地址，它是通过Unsafe进行初始化的，Unsafe是“不安全的”，它提供了java访问底层的能力，通常只有在非常了解它时也使用，例如jdk核心库，用户通常不应该使用它，避免出错。
```
    static {
        try {
            valueOffset = unsafe.objectFieldOffset
                (AtomicInteger.class.getDeclaredField("value"));
        } catch (Exception ex) { throw new Error(ex); }
    }
```
接下来我看常用的getAndIncrement方法，它调用的是Unsafe的getAndAddInt
```
    public final int getAndIncrement() {
        return unsafe.getAndAddInt(this, valueOffset, 1);
    }
```
unsafe.getAndAddInt我们看到了while循环，它会不断进行compareAndSwapInt，成功才返回。
```
    public final int getAndAddInt(Object var1, long var2, int var4) {
        int var5;
        do {
            var5 = this.getIntVolatile(var1, var2);
        } while(!this.compareAndSwapInt(var1, var2, var5, var5 + var4));

        return var5;
    }
```
unsafe.compareAndSwapInt是native方法，定义如下：
```
public final native boolean compareAndSwapInt(Object o, long offset, int expected, int x);
```
o:就是要修改的对象  
offset:是内存偏移地址  
expected:是期望内存中的值  
x:是新的值  
如果判断内存中的值和expected相等，则就改为x

## ABA问题
cas有一个典型的ABA问题，线程1持有当前值是A，线程2将值修改B，线程3将值B又修改为A，此时线程1修改时认为A没被修改过，可以修改成功，但此A非彼A了。如图：
![iamge]()  
解决这个问题非常简单，就是加一个版本号即可，除了判断值，还要判断版本号，每次修改版本号都会改变。
java里通过AtomicStampedReference实现，它内部定义了一个Pair类，维护了一对值和版本号。
```
 */
public class AtomicStampedReference<V> {

    private static class Pair<T> {
        final T reference;
        final int stamp;
        private Pair(T reference, int stamp) {
            this.reference = reference;
            this.stamp = stamp;
        }
        static <T> Pair<T> of(T reference, int stamp) {
            return new Pair<T>(reference, stamp);
        }
    }

    private volatile Pair<V> pair;
}
```
我们看它的compareAndSet方法，它会同时比较值和版本号，casPair调用了Unsafe对象的compareAndSwapObject方法，但这里没有看到while循环，实际上这个在底层方法上实现了，有兴趣的可以继续跟踪源码。
```
    public boolean compareAndSet(V   expectedReference,
                                 V   newReference,
                                 int expectedStamp,
                                 int newStamp) {
        Pair<V> current = pair;
        return
            expectedReference == current.reference &&
            expectedStamp == current.stamp &&
            ((newReference == current.reference &&
              newStamp == current.stamp) ||
             casPair(current, Pair.of(newReference, newStamp)));
    }

public final native boolean compareAndSwapObject(Object var1, long var2, Object var4, Object var5);
```

