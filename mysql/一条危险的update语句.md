假设现在用户的账户表余额是这样的：
```
SELECT id,money,update_time FROM account a WHERE id = 93;
result: id=93 money=10000000 update_time=1608048000000
```  
该账户有1千万的余额...，但是我们对账后发现，由于各种问题，其中有1000块是系统加多的，需要扣除    
所以我们提交了如下的sql去修复这个数据。账户户扣除1000，并且更新一下修改时间，保险一点再加个乐观锁防止更新过程数据有变动，再加个limit 1防止不小心更新到别的数据
```
UPDATE account a SET a.money= a.money - 1000 AND a.update_time = 1608134400000 WHERE id = 93 AND a.update_time=1608048000000 limit 1;
```    
sql写起来很爽，一把梭。执行后如下，我们发现账户余额变成0了...“删库跑路”的悲剧就这么发生了...
```
SELECT id,money,update_time FROM account a WHERE id = 93;
result: id=93 money=0 update_time=1608048000000
```
原因是什么？？？

不少人踩过这坑，update set 语法字段间应该是,分隔，而不是and。用and的话上面的sql就被mysql解析为
```
UPDATE account a SET a.money = (a.money - 1000 AND a.update_time = 1608134400000) WHERE id = 93 AND a.update_time=1608048000000;
```
()实际是一个与操作，a.update_time = 1608134400000 明显是不等于，所以是0，前面再执行与操作也会返回0。
可以select一下  
```
SELECT a.update_time = 1608134400000 FROM account a WHERE id = 93;
result:0
SELECT 1000 AND a.update_time = 1608134400000 FROM account a WHERE id = 93;
result:0
SELECT a.money - 1000 AND a.update_time = 1608134400000 FROM account a WHERE id = 93;
result:0
```

处理数据时一些注意点
1. update set 修改多字段时，注意不要写错成and
2. 使用begin transaction，出错时可以回滚事务
3. 用主键去更新，防止全表扫描和出现死锁。也不要用普通所以和唯一索引。
4. update和delete时，如果知道预期要处理的数据，就加个limit，防止不小心全表修改了

