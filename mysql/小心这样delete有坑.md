# 背景     
由于业务变迁，合规要求，我们需要删除大量非本公司的数据，涉及到上百张表，几个T的数据清洗。我们的做法是先从基础数据出发，将要删除的数据id收集到一张表，然后再由上往下删除子表，多线程并发处理。   
我们使用的是阿里的polardb，完全兼容mysql协议，5.7版本，RC隔离级别。删除过程一直很顺利，突然有一天报了大量：**“Lock wait timeout exceeded; try restarting transaction”**。从日志上看是获取锁失败了，马上想到出现死锁了，但我们使用RC，这个隔离级别下会出现不可重复读和幻读，但没有间隙锁等，并发效率比较高，在我们实际应用过程中，也很少遇到加锁失败的问题。     

单从日志看我们确实先入为主了，以为是死锁问题，但sql比较简单，表数据量在千万级别，其中task_id和uid均有索引，如下：   
```
delete from t_table_1 where task_id in (select id from t_table_2 where uid = #{uid})
``` 
拿到报错的参数，查询要删除的数据也不多，联系dba同学确认没有死锁日志，但出现大量慢sql，那为什么这条sql会是慢sql呢？   

# 问题复现     
表结构简化如下：   
```
CREATE TABLE `t_table_1` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `task_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB;

CREATE TABLE `t_table_2` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `uid` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_uid` (`uid`)
) ENGINE=InnoDB;
```

开始我们拿sql到数据库查询平台查库执行计划，无奈这个平台有bug，delete语句无法查看，所以我们改成select，“应该”是一样。这个“应该”加了双引号，导致我们走了一点弯路。   
```
EXPLAIN SELECT * from t_table_1 where task_id in (select id from t_table_2 where uid = 1)
```
explain后可以看到是走了索引的    
![image](https://github.com/jmilktea/jtea/blob/master/mysql/images/semijoin-1.png)    

到这里可以总结：   
1. 没有死锁，这点比较肯定，因为没有日志，也符合我们的理解。   
2. 有慢sql，这点比较奇怪，通过explain select语句是走索引的，但数据库慢日志记录到，全表扫描，不会错。       

那是select和delete的执行计划不同吗？正常来说应该是一样的，delete无非就是先查，加锁，再删。   
拿到本地环境执行再次查看执行计划，发现确实不同，select的是一样的，但delete的变成全表扫描了。   
![image](https://github.com/jmilktea/jtea/blob/master/mysql/images/semijoin-2.png)    

首先这就符合问题现象了，虽然没有死锁，但每个delete语句都全表扫描，相当于全表加锁，后面的请求就只能等待释放锁，等到超时就出现“Lock wait timeout exceeded”。   
那为什么delete会不走索引呢，接下来我们分析一下。    

# 分析           
```
select * from t_table_1 where task_id in (select id from t_table_2 where uid = #{uid})
```    
回到这条简单sql，包含子查询，按照我们的理解，mysql应该是先执行子查询：select id from t_table_2 where uid = #{uid}，然后再执行外部查询：select * from t_table_1 where task_id in()，但这不一定，例如我关了这个参数：     
```
set optimizer_switch='semijoin=off';
```
这里我们先不用管这个参数的作用，下面会说到。   
关闭后上面的sql就变成先扫描外部的t_table_1，然后再逐行去匹配子查询了，假设t_table_1的数据量非常大，那全表扫描时间就会很长，我们可以通过optimizer_trace证明一下。    
optimizer_trace是mysql一个跟踪功能，可以跟踪优化器做的各种决策，包括sql改写，成本计算，索引选择详细过程，并将跟踪结果记录到INFORMATION_SCHEMA.OPTIMIZER_TRACE表中。   
```
set session optimizer_trace="enabled=on";
set OPTIMIZER_TRACE_MAX_MEM_SIZE=10000000; -- 防止内容过多被截断   
SELECT * from t_table_1 where task_id in (select id from t_table_2 where uid = 1)
SELECT * FROM INFORMATION_SCHEMA.OPTIMIZER_TRACE;
```
输出结果比较长，这里我只挑选主要信息
```
"steps": [
    {
        "expanded_query": "/* select#2 */ select `t_table_2`.`id` from `t_table_2` where (`t_table_2`.`uid` = 1)"
    },
    {
        "transformation": {
            "select#": 2,
            "from": "IN (SELECT)",
            "to": "semijoin",
            "chosen": false
        }
    },
    {
        "transformation": {
            "select#": 2,
            "from": "IN (SELECT)",
            "to": "EXISTS (CORRELATED SELECT)",
            "chosen": true,
            "evaluating_constant_where_conditions": [
            ]
        }
    }
]

"expanded_query": "/* select#1 */ select `t_table_1`.`id` AS `id`,`t_table_1`.`task_id` AS `task_id` from `t_table_1` where <in_optimizer>(`t_table_1`.`task_id`,<exists>(/* select#2 */ select `t_table_2`.`id` from `t_table_2` where ((`t_table_2`.`uid` = 1) and (<cache>(`t_table_1`.`task_id`) = `t_table_2`.`id`)))) limit 0,1000"
```
sql简写一下就是
```
select * from t_table_1 t1 where exists (select t2.id from t_table_2 t2 where t2.uid = 1 and t1.task_id = t2.id)
```
可以看到in可以改成semijoin或exists，最终优化器选择了exists，因为我们关闭了semijoin开关。    
按照这条sql逻辑查询，将会遍历t_table_1表的每一行，然后代入子查询看是否匹配，当t_table_1表的行数很多时，耗时将会很长。     
通过explain观察执行计划可以看到t_table_1进行了全表扫描。   
备注：想查看优化器改下后生成的sql，也可以通过show extended + show warnings：  
```
explain extended SELECT * from t_table_1 where task_id in (select id from t_table_2 where uid = 1);
show warnings;
```

接着我们打开上面的参数开关，再次optimizer_trace跟踪一下   
```
set optimizer_switch='semijoin=on';
```
得到如下：
```
"steps": [
    {
        "expanded_query": "/* select#2 */ select `t_table_2`.`id` from `t_table_2` where (`t_table_2`.`uid` = 1)"
    },
    {
        "transformation": {
            "select#": 2,
            "from": "IN (SELECT)",
            "to": "semijoin",
            "chosen": true
        }
    }
]

"expanded_query": "/* select#1 */ select `t_table_1`.`id` AS `id`,`t_table_1`.`task_id` AS `task_id` from `t_table_1` semi join (`t_table_2`) where (1 and (`t_table_2`.`uid` = 1) and (`t_table_1`.`task_id` = `t_table_2`.`id`)) limit 0,1000"
```
sql简写一下就是
```
select * from t_table_1 semi join t_table_2 where (`t_table_2`.`uid` = 1 and `t_table_1`.`task_id` = `t_table_2`.`id`)"
```
可以看到优化器这次选择将in转换成semijoin了，观察执行计划可以看到走了索引。    

那如果换成delete呢？同样保持开关打开，跟踪如下：   
```
"steps": [
    {
        "expanded_query": "/* select#2 */ select `t_table_2`.`id` from `t_table_2` where (`t_table_2`.`uid` = 1)"
    },
    {
        "transformation": {
            "select#": 2,
            "from": "IN (SELECT)",
            "to": "semijoin",
            "chosen": false
        }
    },
    {
        "transformation": {
            "select#": 2,
            "from": "IN (SELECT)",
            "to": "EXISTS (CORRELATED SELECT)",
            "chosen": true,
            "evaluating_constant_where_conditions": [
            ]
        }
    }
]
```    
可以看到和关闭semijoin一样，对于delete优化器也是选择了exists，我们表是千万级别，全表扫描加锁，其它操作语句自然都会超时获取不到锁而失败。   

# semijoin    
**semijoin**翻译过来是半连接，是mysql针对in/exists子查询进行优化的一种技术，[参见文档](https://dev.mysql.com/doc/refman/8.0/en/semijoins.html)。  
可以使用SHOW VARIABLES LIKE 'optimizer_switch';查看semijoin是否开启。      
上面使用IN-TO-EXISTS改写后，外层表变成驱动表，效率很差，那如果使用inner join呢，使用条件过滤后，用小表驱动大表，但join查询结果是会重复的，和子查询语义不一定相同。如：   
```
SELECT class.class_num, class.class_name
    FROM class
    INNER JOIN roster
    WHERE class.class_num = roster.class_num;
```
这样会查询出多条相同class_num的记录，如果子查询，那么查询出来的class_num是不一样的，也就是去重。当然也可以加上distinct，但这样效率比较低。    
```
SELECT class_num, class_name
    FROM class
    WHERE class_num IN
        (SELECT class_num FROM roster);
```

semijoin有以下几种策略，以下是官方的解释：
```
Duplicate Weedout: Run the semijoin as if it was a join and remove duplicate records using a temporary table.

FirstMatch: When scanning the inner tables for row combinations and there are multiple instances of a given value group, choose one rather than returning them all. This "shortcuts" scanning and eliminates production of unnecessary rows.

LooseScan: Scan a subquery table using an index that enables a single value to be chosen from each subquery's value group.

Materialize the subquery into an indexed temporary table that is used to perform a join, where the index is used to remove duplicates. The index might also be used later for lookups when joining the temporary table with the outer tables; if not, the table is scanned. For more information about materialization, see Section 8.2.2.2, “Optimizing Subqueries with Materialization”.
```
以Duplicate Weedout为例，mysql会先将roster的记录以class_num为主键添加到一张临时表，达到去重的目的。接着扫描临时表，每行去匹配外层表，满足条件则放到结果集，最终返回。   
具体使用哪种策略是优化器根据具体情况分析得出的，可以从explain的extra字段看到。    

**那么为什么delete没有使用semijoin优化呢？**       
这其实是mysql的一个bug，[bug地址](https://bugs.mysql.com/bug.php?id=35794)，描述场景和我们的一样。  
文中还提到这个问题在mysql 8.0.21被修复，[地址](https://dev.mysql.com/worklog/task/?id=6057)   
![image](https://github.com/jmilktea/jtea/blob/master/mysql/images/semijoin-3.png)    

大致就是解释了一下之前版本没有支持的原因，提到主要是因为单表没有可以JOIN的对象，没法进行一些列的优化，所以单表的UPDATE/DELETE是无法用semijoin优化的。    
这个优化还有一些限制，例如不能使用order by和limit，我们还是应该尽量避免使用子查询。    
在我们的场景通过将子查询改写为join即可走索引，现在也明白为什么老司机们都说尽量用join代替了子查询了吧。    

