mysql有没有解决幻读问题，在网上的答案比较多，有的斩钉截铁的说有，有的说解决部分情况下，如果有解决是使用什么方式解决的，这也是一个比较常见的面试题，我们来分析一下。     

首先什么是幻读，就是在事务内的两次读，读到不同的行集。举个栗子：   
```
-- 假设现在有id：1,5,10,15

start transaction;
-- 第一次读返回1,5,10
select * from t_table where id between 1 and 10;

-- 第二次读返回1,5,6,10
select * from t_table where id between 1 and 10;
```

假设上述的情况在第一次读后，另一个事务往表里插入一条id=6的记录，若第二次读比第一次读返回的结果了一条6的记录，这条记录就称为幻行，这种情况就称为幻读。     
有的同学可能会混淆不可重复读和幻读，因为本质上两者都是多次读取到不一样的结果，但是幻读侧重于行集，读到的数据多了或少了，这一点我们这[MVCC原理]()这一篇也有提及，MVCC原理这一篇也推荐阅读，读完后会更好理解本篇的内容。    
另外我们也可以看mysql官网对幻读的描述：[Phantom Rows](https://dev.mysql.com/doc/refman/8.0/en/innodb-next-key-locking.html)    

**前提**    
1. 在讨论幻读，首先要说明当前使用的事务隔离级别是什么，网上很多文章脱离事务隔离级别在讨论的，都是耍流氓。     
我们这里讨论的事务隔离级别是：可重复读(RR)，这也是mysql默认的事务隔离级别，其它事务隔离级别在讨论幻读是没有意义的。    

2. 多版本并发控制(MVCC)，这一点我们在[MVCC原理]()介绍了，mysql通过MVCC实现并发访问，提升效率，MVCC里面有两个重要概念，版本链和一致性视图，同时提到了快照读和当前读的区别        

3. next-key lock，RR下加锁的基本单位，是一个前开后闭的区间，是间隙锁和行锁的组合，可提交读(RC)下是没有的     

4. mysql版本，这里我们基于5.7.34，对于不同版本next-key lock可能有不同表现，例如mysql在高版本修复了一些bug    

**例子**    
```
CREATE TABLE `t_test` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `age` int(11) DEFAULT NULL,
  `name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `id_num` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uidx_id_num` (`id_num`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB;

INSERT INTO `t_test` (`id`, `age`, `name`, `id_num`) VALUES (1, 19, 'tom', 'tom000');
INSERT INTO `t_test` (`id`, `age`, `name`, `id_num`) VALUES (5, 15, 'jack', 'jack000');
INSERT INTO `t_test` (`id`, `age`, `name`, `id_num`) VALUES (15, 22, 'lucy', 'lucy000');
```    

例子1，首先我们使用快照读，如图：   
![image](https://github.com/jmilktea/jtea/blob/master/mysql/images/phantom-row1.png)    
这两次读的结果是一样的，不会出现幻读，原因是我们再MVCC原理篇分析的，在RR下，对于快照读，一致性视图只会生成一次，所以第一次读到什么后面读到的都是一样的。

例子2，我们使用当前读，如图：   
![image](https://github.com/jmilktea/jtea/blob/master/mysql/images/phantom-row2.png)    
可以看到session2阻塞了，并不能插入数据，所以session1的两次读也都能读到一样的结果。那么session2为什么会阻塞呢？这就是next-key lock的作用。    
**next-key lock** 是间隙锁和行锁的组合，是RR下加锁的基本单位，引用下[极客时间《MySQL 实战 45 讲》](https://time.geekbang.org/column/article/75659)中next-key lock加锁规则的总结：   
原则 1：加锁的基本单位是 next-key lock，next-key lock 是前开后闭区间。    
原则 2：查找过程中访问到的对象才会加锁，例如访问普通索引就对普通索引加锁，访问主键索引就对主键索引加锁。    
优化 1：索引上的等值查询，给唯一索引加锁的时候，next-key lock 退化为行锁。     
优化 2：索引上的等值查询，向右遍历时且最后一个值不满足等值条件的时候，next-key lock 退化为间隙锁。    
一个 bug：唯一索引上的范围查询会访问到不满足条件的第一个值为止。    

在session1中，首先会对(-∞,1]加间隙锁，由于是唯一索引，所以退化为行锁，锁定id=1行。接着继续下后查找，会对(1,5]加锁，此时已经满足结束条件。    
但由于上面提到的bug，会继续向后寻找到第一个不满足条件的数据，会对(5,15]加锁。这个问题在mysql8.0高版本已经被修复。    
session2 insert id=2的数据，所以会阻塞，既然数据都插不进去了，那么session1的多次读自然都是读到同一个结果。       

例子3，那如果是快照读和当前读混用呢，如图：    
![image](https://github.com/jmilktea/jtea/blob/master/mysql/images/phantom-row3.png)    
这种情况下就出现幻读了，因为当前读并不从上一个查询的一致性视图读取，而是读取最新的数据，所以出现不一致的情况，从这个角度来说，mysql并没有完全解决幻读。     

**总结**    
从上面的分析可以看到，要分析幻读首先要在RR可重复读事务隔离级别下讨论，RC下肯定会出现幻读，序列化隔离级别下肯定不会出现幻读，所以其它事务隔离级别的讨论都没有意义。    
其次要从MVCC和next-key lock分析，前者通过使用同一个一致性视图，解决了快照读的幻读问题，后者使用next-key lock解决幻读问题，next-key lock用更复杂的加锁规则，来保证事务读取数据的一致性，但也由于这样使得性能较低，在对并发性能要求较高的场景推荐使用RC事务隔离级别，RC下没有间隙锁，自然也没有next-key lock，效率较高。    
最后，如果混用快照读和当前读，mysql还是会出现脏读的。    





