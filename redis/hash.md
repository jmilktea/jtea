## 简介  
hash是redis基本数据类型中的一种，用于存储key-value字典数据结构，使用非常广泛。    
字典结构如下：
```
typedef struct dict { 
    dictType *type;  
    void *privdata;  
    dictht ht[2];    
    long rehashidx;   
    int iterators;   
} dict;
```
type表示字典类型，privdata表示私有数据    
ht是一个大小为2的数组，类型是dictht，至于长度为什么是2与redis rehash机制有关，下面会讲到   
rehashidx表示rehash的进度，-1表示rehash未进行   
interatros表示当前正在迭代的迭代器数   

dictht结构如下： 
```
typedef struct dictht { 
    dictEntry **table;   
    unsigned long size;  
    unsigned long sizemask; 
    unsigned long used;  
} dictht; 
```   
table是hash数组，存储的是dictEntry节点元素    
size表示hash表的大小，sizemask等于size-1，用于计算数组索引值     
used表示已有节点数量，reids会通过一个变量保存长度，所以通过hlen命令获取大小时间复杂度为O(1)       

dictEntry表示节点，结构如下：
```
typedef struct dictEntry {
    void *key;
    union {
        void *val;
        uint64_t u64;
        int64_t s64;
    } v;
    struct dictEntry *next;
} dictEntry;
```
key表示键，v表示值     
next是一个指向下一个节点的指针，用于解决hash冲突，redis使用“链表地址法”解决hash冲突，java8中在冲突节点不超过8时也使用该方式，超过8时使用红黑树。    

上面涉及到3个数据结构，如下图    
![image](https://github.com/jmilktea/jmilktea/blob/master/redis/images/hash-1.png)    

## 渐进式rehash     
java中的hash创建是默认大小是16，在满足负载因子时(默认0.75)，就会发生扩容，扩大为原来的2倍。这是由于hash底层是使用数组结构，数组节点使用快满时，如果不扩容势必会产生大量的hash冲突，导致很多元素都存放在同一个位置，影响遍历的效率。扩容是有成本的，需要创建一个新的hash，把原来的元素的元素搬过去，所有也不能经常扩容，0.75就是个阈值，存储占比达到这个比例就触发扩容。另外java的hash是不支持动态缩容的，例如当我们扩容到10000，后续的逻辑删除9999个时，实际大小还是10000，java并没有自动缩容的机制。    

那么redis中的hash是如何扩容/缩容的呢？这也是面试非常常见的一个问题。   
redis中hash的扩容/缩容机制称为：**渐进式rehash**  
首先什么是渐进式，我们知道redis的核心是单线程操作的，如果像java一样进行扩容就会出现阻塞，其它命令就只能等待，所以redis的hash不是一次性完成扩容的，而是分步，渐进式的慢慢实现扩容。    
redis hash默认创建大小为4的容器，当出现以下条件时就会触发扩容：  
1.redis没有在执行BGSAVE/BGREWRITEAOF操作，且负载因子大于或等于1  
2.redis正在执行BGSAVE/BGREWRITEAOF操作，且负载因子大于或等于5   
负载因子的计算公式是：load_factor = ht[0].used / ht[0].size   
BGSAVE/BGREWRITEAOF 是redis持久化数据RDB/AOF的两个命令，至于为什么执行这两个命令时负载因子会变成5，我们下面说到，扩容过程是一样的，先看没有执行这两个命令时的情况。  

不断往hash添加元素，负载因子就会大于等于1，redis就会触发扩容操作，过程如下：   
1.为ht[1]分配空间，此时hash同时持有ht[0],ht[1]。扩容大小为：大于used * 2的第一个2的n次幂，例如当前used是16，则扩大为32    
2.将rehashidx字段设置为0，表示rehash正式开始   
3.rehash是以hash桶bucket为单位进行的，每次迁移会把这个位置上的所有元素迁移（整个链表）。迁移分为两种，主动rehash和惰性rehash，这种方式和key的过期是一样的。   
主动rehash是redis后台的定时任务进行的，定时会迁移一定数量的bucket   
被动rehash是每次操作ht[0]的bucket时对该桶进行迁移    
每次迁移后rehashidx值就+1   
4.在rehash期间，ht[0]不能再增加，否则迁移就没完没了。对于hash的相关操作，读会先读ht[0]，读不到读ht[1]，对于修改和删除，ht[0]ht[1]都会操作，对于新增，只会在ht[1]新增，这样就保证ht[0]的数量不会再增加了。  
5.随着时间的不断进行，ht[0]的所有元素就会被完全迁移到ht[1]，rehashidx值置为-1表示迁移完成，ht[0]的空间可以释放，并指向ht[1]，ht[1]重新置为空     

redis hash是有缩容机制的，内存是redis最重要的资源，所以当空间不再需要时，需要释放出来。  
redis hash缩容的条件是当哈希表的负载因子小于0.1时，整个过程和扩容是类似的，不再累述。   

上面我们还留了一个问题，为什么在执行BGSAVE/BGREWRITEAOF时，负载因子会变成5。这个跟redis数据持久化的机制有关。   
BGSAVE/BGREWRITEAOF是redis通过fork一个子进程进行的，通常情况下，创建子进程需要拷贝父进程的所有资源，例如数据段、堆、栈，效率很低，所以为了提高子进程的创建效率，许多操作系统都通过“写时复制”来优化这个过程。  
**写时复制**  
父进程在创建子进程时不会拷贝任何数据，父子进程共享一份资源，这样创建效率就很高了，仅当父进行对资源进行写的时候，才重新拷贝到子进程。   
如果在执行BGSAVE/BGREWRITEAOF时，频繁触发扩容，就会导致频繁写，子进程需要频繁复制数据，影响性能，所以redis在此时调高了负载因子，避免这种性能消耗。   




