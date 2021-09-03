## 前言   
慢sql对我们应用有非常大的影响，一个慢sql语句很可能会拖垮整个应用，所以平时写sql语句时我们需要特别注意性能问题，常见需要考虑的点如下：  
1.select有没有走索引，有没有使用正确的索引   
2.索引建得是否合理，区分度如何   
3.是否一次操作太多数据，应该分页   
4.多表关联，是否应该拆开查询   
5.是否使用了排序，排序字段有没有索引   
6.表数据量是否过大，是否应该分表  
...

关于索引问题这里不再详述，例如哪些字段适合建索引，哪些不适合。我们可以通过show index from索引名称查看表的索引信息，其中**cardinality**字段比较重要，表示索引的区分度，当区分度越高索引的效果就越好，否则越差，这也是为什么我们说“性别”这种枚举字段不适合建索引的原因。   
![image]()    

虽然我们知道这些思想，并且遵循一些开发规范，但问题还是可能出现，怎么排查和分析就显得特别重要了。  

## 慢日志   
slow_query_log是mysql常见日志中的一种，专门用于记录慢sql语句，所谓的慢是执行时间大于long_query_time参数的sql语句，mysql会把这些语句都记录到慢查询日志，提供分析。  
默认long_query_time为10s，也就是大于10s才算慢sql需要记录，通常我们生产环境会配置一个较小的值，如：1s   
```
-- 设置慢查询时间
SHOW VARIABLES LIKE '%LONG_QUERY_TIME%';
SET GLOBAL LONG_QUERY_TIME = 1;
-- 查看慢查询是否开启，慢查询日志路径
SHOW VARIABLES LIKE '%slow_query_log%';
SHOW VARIABLES LIKE '%slow_query_log_file%';
```

当我们执行一个慢sql后，可以到上面的路径下找到对应的日志   
![image]()   

抓取到慢sql时是第一步，如何去分析和优化才是关键。      

## EXPLAIN    
explain用于输出sql语句的执行计划，可以帮助我们分析sql的执行顺序，使用了哪个索引等。如下：  
```
EXPLAIN SELECT*FROM account a WHERE id = 1000;
```
EXPLAIN的输出字段：  
- id  
指示sql的执行步骤顺序，如果id相同，则从上到下的顺序执行，如果id不同，则按id从到大小执行   
- select_type   
SIMPLE:简单的select，没有子查询和union   
PRIMARY:有子查询，最外层的为PRIMARY  
UNION:union中，第二个select  
UNION RESULT:union的结果   
SUBQUERY:子查询   
- table   
查询用到表的名称   
- type  
以下类型性能从高到低排序  
system:表中只有一行数据时    
const:通过索引一次就能找到，主键或者唯一索引   
eq_ref:唯一性索引扫描，通常是在表关联时出现，通过主键或者唯一键关联找到关联表的数据   
ref:非唯一性索引时，找到所有匹配数据   
range:索引范围查找，使用>,<,in等都属于范围查询  
index:索引扫描，通常效率也很低了，和表扫描是半斤八两  
all:全表扫描，性能最差   
- possiable_key/key/key_length  
可能选择的索引和最终选择的索引，已经索引的长度     
- rows   
mysql预估要扫描的行数   
- filtered  
选择的行数和读取的行数的百分比   
- extra  
额外信息，常见的有   
using filesort:当排序没有使用到索引时会出现，这样mysql需要把数据读取出来后再次排序   
using index:使用了覆盖索引，不需要回表   
using index condition:使用了“索引下推”，在存储引擎层就过滤数据，不用把数据回到server层再过滤   
using where:存储引擎查询数据后再在server层使用where过滤数据    
using temporary：使用了临时表       

上面我们主要关注的字段是type,key,extra。这三个字段告诉我们有没有使用索引，使用了什么类型的索引，一些重要的额外参考信息。   
当我们的sql写得很复杂时，explain的结果就会比较多，优化起来也会比较麻烦，所以我们的建议是尽量保证sql简单，尽量不要过多的join和子查询。   
![image](4)   

## PROFILE   
explain告诉我们执行计划，我们大概可以猜测到一些东西，但是它仅仅告诉我们“计划”是怎么样的，没有告诉我们详细的步骤，例如一条sql慢了，到底是慢在哪里，查询慢还是排序慢，是获取锁慢还是mysql本身就慢，explain没有告诉我们这些。    
**profile**是mysql提供用于分析一条sql执行过程的工具，我们看一个实际例子。   

![image](5)   
如上的sql，表大概有800w数据(一些字段我们用“column”代替掉)，实际执行接近20s，是一个很慢的sql。   
我们看到explain走了user_code这个索引，但是扫描的行数比较多，extra出现using filesort信息。接下来使用profile来分析一下到底慢在哪。  
查看是否开启profile，没有的话需要开启下       
```
show variables like 'have_profiling';
set profiling=1;
``` 
接着执行我们的sql语句，再执行：show profiles; 会得到这段时间内的结果，如下：  
![iamge](6)  

拿到id，再执行：show profile for query 2; 得到分析结果，如下：  
![image](7)   
上面就是这次sql执行的整个过程，我们定位到最耗时的步骤create sort index，证明我们的sql慢在排序这里，上面我们的sql用了两个排序字段，并且没有建立索引，mysql大量时间都在处理排序，这里即是我们要优化的地方。   
上面的步骤很多，但是基本都能知道是什么意思，也是mysql一条完整sql的执行流程，例如包括了：检查权限，获取锁，优化器优化，执行sql，排序，慢日志记录记录等，都是我们熟悉的步骤。      
profile非常简单实用，但在mysql5.6以后就逐渐被performance schema代替了。   

## Performance Schema   
Performance Schema翻译过来是：性能模式的意思，相比profile更加强大，可以在更多的过程记录更多的信息，用于分析。这些记过都保存在Performance Schema库中，这是mysql已经创建好的一个数据库。   
![image](8)   

我们可以使用Performance Schema完成和profile一样的功能   
开启配置，可能是已经开启的，也可以只选择关注的开启   
```
UPDATE performance_schema.setup_instruments
       SET ENABLED = 'YES', TIMED = 'YES'
       WHERE NAME LIKE '%statement/%';

UPDATE performance_schema.setup_instruments
       SET ENABLED = 'YES', TIMED = 'YES'
       WHERE NAME LIKE '%stage/%';
```
开启配置，可能是已经开启的，也可以只选择关注的开启
```
UPDATE performance_schema.setup_consumers
       SET ENABLED = 'YES'
       WHERE NAME LIKE '%events_statements_%';

mysql> UPDATE performance_schema.setup_consumers
       SET ENABLED = 'YES'
       WHERE NAME LIKE '%events_stages_%';
```
执行sql  
```
SELECT * FROM employees.employees WHERE emp_no = 10001;
```  
查看sql的事件id   
```
SELECT EVENT_ID, TRUNCATE(TIMER_WAIT/1000000000000,6) as Duration, SQL_TEXT
       FROM performance_schema.events_statements_history_long WHERE SQL_TEXT like '%10001%';
```   
查看结果   
```
SELECT event_name AS Stage, TRUNCATE(TIMER_WAIT/1000000000000,6) AS Duration
       FROM performance_schema.events_stages_history_long WHERE NESTING_EVENT_ID=31;
```

Performance Schema还有更多的功能，参考[这里](https://dev.mysql.com/doc/refman/5.7/en/performance-schema.html)   