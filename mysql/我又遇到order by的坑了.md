## 背景    
在实际开发中，我需要扫描表里符合某些条件的数据，使用id分页偏移，每次取1000条，sql语句也很简单如下：   
```
  select * from t_bill b
    where b.id > 0
    and b.repayment_date = '2022-10-25'
    and b.status = 0        
    and b.type = '10001'
    and b.c_status = 1
  order by b.id
  limit 1000;
```
t_bill表数据几千万，id是自增主键，repatment_date有普通索引。    
当我拿这条sql到生产读库查询时，几分钟都查询不出来，sql执行计划如下：  
![image](https://github.com/jmilktea/jtea/blob/master/mysql/images/orderby-221102-1.png)   
从执行计划可以看出sql走了主键索引，扫描的行数非常多，实际执行起来也非常慢。    

问题是这个扫描场景我们在另一个地方也用到，如果真那么慢，那里应该早爆出问题才是，于是我好奇的找了一下那里的代码，发现如下：  
```
  select * from t_bill b
    where b.id > 0
    and b.repayment_date = '2022-10-25'
    and b.status = 0        
    and b.type = '10001'
    and b.c_status = 1
    **and b.create_time < #{now}**
    order by b.id
  limit 1000;
```
为一的区别就是加粗那行，创建时间小于当前时间，而create_time字段是没有索引的，开发当时的意愿是只扫描任务开始时前的数据，后面创建的数据不再扫描范围内。   
也就是说，create_time < now 这个条件能过滤的数据实际非常少，如果没有新创建的数据，那么这个条件就是个无用条件，实际情况也基本是这样。   
如果这里很慢业务应该早出问题了，那什么这里不会出现问题呢，我们看下加了这个条件的执行计划：  
![image](https://github.com/jmilktea/jtea/blob/master/mysql/images/orderby-221102-2.png)    
从执行计划可以看出和第一个有很大区别，type从range变成了index_merge，key变成了index_repaymen_date,PRIMARY，从Extra信息也可以看到使用到了Using intersect(index_repaymen_date,PRIMARY)，最关键的是扫描行数大大减少了，所以实际执行也快了很多。   

**index_merge 索引合并**    
这是mysql优化器的一个优化，当我们的sql where语句用到多个索引时，mysql会考虑同时使用多个索引同时查询数据，然后再对结果集进行处理。   
例如我们上面mysql就同时用了主键索引和repayment_date的普通索引，查询数据后再取交集，因为我们索引条件之间用的是and。   
从执行计划的type index_merge可以看出使用了索引合并，并且是交集，Extra字段显示的是Using intersect，如果使用的是or，可能就是Using union。   
索引合并选项开关默认就是开启的，可以通过index_merge参数设置。    

**那么问题来了，为什么多了个无用条件，差别就这么大！**    

## 分析   
mysql选择执行计划是基于代价进行选择的，这个代价包括cpu代价，磁盘io代价，内存代价，通过分析每种选择的代价，最终得出一个它认为最优的结果。    
从explain我们只能看到结果，没法看到细节，那就需要从优化器跟踪分析，使用如下语句：   
```
set session optimizer_trace="enabled=on";
set OPTIMIZER_TRACE_MAX_MEM_SIZE=1000000; --防止内容过多被截断   
-- your sql
SELECT * FROM INFORMATION_SCHEMA.OPTIMIZER_TRACE;
set optimizer_trace="enabled=off";
```
```
  select * from t_bill b
    where b.id > 0
    and b.repayment_date = '2022-10-25'
    and b.status = 0        
    and b.type = '10001'
    and b.c_status = 1
    and b.create_time < 1667318400000
    order by b.id
  limit 1000;
```
首先我们看下加了create_time(取当前时间戳)时的跟踪分析，由于输入内容较多，我们截取重要的部分看    
![image](https://github.com/jmilktea/jtea/blob/master/mysql/images/orderby-221102-3.png)   
![image](https://github.com/jmilktea/jtea/blob/master/mysql/images/orderby-221102-4.png)    
从上面的分析可以看出本次查询可能选择的所以以及它们的代价，还有选择合并索引的代价是最低的，并且在最后mysql也选择了索引合并这个执行计划。   

那么去掉create_time这个条件呢？我们看下输出：   
![image](https://github.com/jmilktea/jtea/blob/master/mysql/images/orderby-221102-5.png)   
![image](https://github.com/jmilktea/jtea/blob/master/mysql/images/orderby-221102-6.png)     
从图中可以看出，mysql优化器依然认为使用索引合并的代价是最低的，和上面加了create_time时是一样的分析结果。到这里只是优化器打算使用，还不是最终使用。问题就出现在后面，如下：   
![image](https://github.com/jmilktea/jtea/blob/master/mysql/images/orderby-221102-7.png)     
由于我们的程序使用了limit 1000，触发了优化器的再次选择，从图中可以看到recheck reason是**low_limit**。在重新选择的过程，优化器放弃了index_repayment_date这个索引，进而也放弃了索引合并，最终选择了主键索引。    
这个low limit是什么鬼？从字面意义上看是行数太少，那如果我们从limit 1000改成limit 2000呢？奇迹发生了~，执行计划如下：   
![image](https://github.com/jmilktea/jtea/blob/master/mysql/images/orderby-221102-8.png)    
可以看到它又选择了正确的执行计划，实际2000这个数改成1500也一样，但是改成1100就又不同了，所以具体跟数据的某个占用比率有关，导致mysql优化器不同的选择。    
在实际开发中，我们一般不会一次分页查询太多数据，而如果这个数字影响执行计划，那就太难选择了，显然limit不是罪魁祸首。   

另一个关键点就是我们使用了**order by limit**，这个组合是有"bug"的，我们在这篇也分析过：[order by居然有bug?](https://github.com/jmilktea/jtea/blob/master/mysql/orderby%20bug.md)    
难道今天又重蹈覆辙了？答案是：是的！   
我们去掉order by id，查看执行计划，就会发现也和上面那个较好的一样了。问题的根本是：**我们使用了order by limit，并且limit是个较小的数，优化器会认为排序是个昂贵的操作，并且最终选择的条数又很少，那么选择这个字段索引查询，可以避免排序，于是放弃了前面的分析，选择了这个“错误”的计划。**       

根据上面的文章，我们可以有如下解决方式：   
1. limit 更多的行数，不推荐使用
2. force index，强制使用指定索引
3. mysql 5.7.33以上版本，关闭prefer_ordering_index选项（prefre翻译过来是更喜欢的意思，从这里也可以看出优化器喜欢错了）

本次在网上还看到另外两种解决方式   
4. 使用order by (id + 0)，欺骗优化器，通过运算让优化器放弃id索引的考虑    
5. 使用order by id,repayment_date，也就是把我们的索引字段也加进去排序，推荐这种做法。    

order by limit 的使用非常频繁，这个问题非常容易掉坑，使用时需要谨慎。这已经不是我第一次遇到这个问题，但最开始还是没往这个方向想，甚至还问了公司的dba，想不到再次入坑，记忆深刻了。
