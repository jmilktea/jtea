## 前言  
mysql里有以下几种常见的日志，用于协助完成一些高级功能，如事务的持久性、原子性，主从同步，MVCC等，其中最主要的就是redo log、undo log、binlog这三种日志，了解它们的工作原理对于我们学习mysql高级特性有很大的帮助，同时在面试时也经常会被问到，如：  
1. redo log、undo log、binlog 分别有什么作用    
2. 为什么同时需要redo log和binlog    
3. redo log，binlog 如何保证一致性    

接下来我们就来认识以下这三种日志   
![image](https://github.com/jmilktea/jmilktea/blob/master/mysql/images/mysql-log.png)  

## binlog  
binlog是mysql server层提供的，所有存储引擎都能用，例如innodb，myisam，它是二进制日志文件。在[canal](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/canal/canal%E7%AE%80%E4%BB%8B.md)里我们已经有简单的介绍。可以理解为binlog就是记录数据库每次变更，如insert/update/delete/create/truncate，select语句不会记录，因为select不会对数据产生修改。那么记录binlog有什么作用呢？有了这个log，我们就可以知道一条数据的完整的变动过程，可以用于复制和恢复数据。  

**binlog作用**
- 主从同步  
- 恢复数据  

**binlog模式**  
由于记录的内容不同，binlog有三种模式，默认是statement
- statement level
- row level
- mixed  

**查看binlog**  
可以使用show binary logs命令查看当前有哪些binlog文件，这些文件的内容是二进制格式，无法直接查看。我们可以通过以下命令来解码
```
mysqlbinlog --base64-output=decode-rows -v --start-datetime="2020-06-04 07:21:09" --stop-datetime="2020-06-05 07:59:50" mysql-bin.000003 --base64-output=decode-rows
```
显示的内容如下  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/canal/images/binlog-statement.png)  
可以看到主要有sql语句，执行的时间，事务id等信息  

## redo log  
redo log按名字理解它就是重做日志，也就是在某种情况下，根据日志把没来得及做的事情重做一遍，保证数据的正确性，提供一种crash safe能力，这种情况一般就是mysql服务挂了，而数据还没写到硬盘上。  
为了保证性能，mysql不会每次修改都刷盘，这样效率太低。而是把数据页加载到内存中，在内存修改，然后再异步写到磁盘。随之而来的问题是如果还没刷盘时mysql服务挂了，那么数据如何落盘？redo log就是为了解决这个问题，mysql在内存修改完数据后，就会写redo log到磁盘，所以redo log是持久化的，如果mysql挂了，重启后就会通过redo log重做，把数据恢复到上一次的状态，保证数据的准确性。这也是事务持久性特性的保证。    
那么为什么redo log就可以写磁盘呢，效率不会很低吗？redo log的磁盘写是顺序写，也就是顺序IO，我们知道顺序IO的性能比随机IO要高很多，比直接操作内存不会低很多。类比一下，rocketmq的commit log也是顺序写的。  

**redo log环形结构**     
redo log采用环形写入思路，假设配置redo log有4个文件，环形结构如下：     
![image](https://github.com/jmilktea/jtea/blob/master/mysql/images/mysql-log-redolog-ring.png)   

其中write position表示写入位置，redo log每次都从这个位置往后写。check point表示检查位置，当写到check point的时候表示所有空间都写完了，需要从check point开始往后清理腾出空间才能继续写，write position和check point之间的空白位置就是可写入空间。     

**redo log作用**  
- 保证数据持久性  
- 避免写数据时操作磁盘，提升效率     

## binlog与redo log的关系  
按照上面的叙述，binlog和redo log都是在修改时会产生的日志，那么它们有什么区别呢？  
- 存储位置不同  
binlog是mysql server层生成的，所以所有存储引擎都会产生。而redo log是在存储引擎层实现的，只有innodb支持。    
binlog是由mysql官方提供的，官方最开始的存储引擎是myisam，但myisam不具备crash safe能力。而innodb是另一家公司已插件的形式提供给mysql，现在已经是mysql默认存储引擎。    
- 存储结构不同     
binlog采用的是最加写的方式，如文件mysql-bin.000001，后面的序号会一直递增，可以设置binlog的保存日期。而redo log采用环形写，写满了就清除了。   
- 存储内容不同      
binlog存储的是基于行的逻辑操作语句，也就是具体变动的sql语句，而redo log存储的是基于数据页的物理变化，也就是哪一页将哪个位置的改动。   

**二阶段提交**  
binlog是redo log都是在修改时产生的日志，如果这两个日志不一致，例如先写binlog成功，写redo log失败，或者相反的过程，都会造成数据不一致。mysql通过**两阶段提交**来保证两者的一致。  
- prepare阶段
此时SQL已经成功执行，并生成事务ID(xid)信息及redo的内存日志。此阶段InnoDB会写事务的redo log，但要注意的是，此时redo log只是记录了事务的所有操作日志，并没有记录提交（commit）日志，因此事务此时的状态为Prepare。此阶段对binlog不会有任何操作。
- commit 阶段  
这个阶段又分成两个步骤。第一步写binlog（先调用write()将binlog内存日志数据写入文件系统缓存，再调用fsync()将binlog文件系统缓存日志数据永久写入磁盘）；第二步完成事务的提交（commit），此时在redo log中记录此事务的提交日志（增加commit 标签）  
可以看出，此过程中是先写redo log再写binlog的。但需要注意的是，在第一阶段并没有记录完整的redo log（不包含事务的commit标签），而是在第二阶段记录完binlog后再写入redo log的commit 标签。还要注意的是，在这个过程中是以第二阶段中binlog的写入与否作为事务是否成功提交的标志。  
![image](https://github.com/jmilktea/jmilktea/blob/master/mysql/images/mysql-log-2stages.png)      

其实两阶段解决的问题本身是个分布式系统问题，我们在分布式事务场景也经常听到它，假如没有两阶段提交，先写哪个都会出现问题。   
- 先写redo log，再写binlog      
如果写redo log后崩溃，那么binlog缺失，服务重启后，redo log恢复数据，但binlog缺失。假如有主从同步或者通过binlog备份数据，那么数据就缺失了。    
- 先写binlog，再写redolog    
如果写binlog后崩溃，那么redolog缺失，服务重启后，由于没有持久化，数据依然缺失，但binlog已存在。假如有主从同步或者通过binlog备份数据，那么就会出现数据不一致。      

而使用两阶段提交，假如redo log prepare后奔溃，binlog还没写，服务重启后，发现是prepare阶段，就会根据xid去binlog文件找，如果找不到，就认为本次提交不成功。如果找得到，就认为本次提交成功，redo log会重新commit。如果在binlog写后才奔溃，那也符合这个规律，所以数据最终可以保持完整一致。    

## undo log  
undo log是回滚日志，意味着它记录的是数据的上一个状态（这样才可以回滚）。如事务操作，其中有一个操作失败，就需要把其它操作回滚，这就是事务的原子性，只会同时成功或失败，它是通过undo log实现的。  

**undo log作用**  
- 事务回滚  
- 多版本控制（MVCC）  

![image](https://github.com/jmilktea/jmilktea/blob/master/mysql/images/mysql-log-undolog.png)     
如上图，每个表mysql都会加上默认两个列，DATA_TRX_ID和DATA_ROLL_PTR，前者是本次操作的事务id，mysql每次事务会生成一个递增不重复的id；后缀从名字就可以看出是一个回滚指针，指针指向的是地址，也就是undo log的地址。可以看到事务200修改前，会记录一条undo log，并把事务200的指针指向它，当事务200回滚时，就可以通过该指针找回原来的数据。   
另外是多版本控制MVCC，顾名思义就是一行数据可以有多个版本，这样就可以实现多版本读。如我们的事务隔离级别是可重复读（RR），那么在读取过程中，其它事务修改了记录，如何保证该事务内每次读取到都是一致的呢？这就是通过undo log实现的，生成一个版本，每次都读这个版本即可，这样就保证了多次读不会被其它事务影响。关于MVCC详细可以参考：https://github.com/jmilktea/jmilktea/blob/master/mysql/MVCC%E5%8E%9F%E7%90%86.md      




