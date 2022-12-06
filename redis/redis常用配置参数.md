[redis配置](https://raw.githubusercontent.com/antirez/redis/5.0/redis.conf)还蛮多的，不过注释写得很清楚。这里记录一些需要关注的常用配置。

################################## NETWORK 网络相关 #####################################  
- bind 127.0.0.1  
bind用于绑定本机的ip地址，一旦绑定了就只有通过该ip可以访问redis。例如本机有两张网卡，对应的ip分别为ip1和ip2，如果bind设置为ip1，那么只有通过网卡1的请求才能访问，网卡2的会被拒绝。默认为127.0.0.1，也就是只有本机能访问redis，外部无法访问。另外设置为0.0.0.0表示允许外界通过本机的ip访问，这是不安全的，需要设置密码
- port 6379 
设置端口，默认是6379。有时候为了安全性，可以不使用默认端口，如用16379

################################# GENERAL 常用 #####################################  
- daemonize no  
默认情况redis不是以守护进程运行，当退出命令行时redis server就会退出。通常会设置为yes让redis在后台运行  
- databases 16  
数据库数量，默认就是16个，如果我们在客户端不指定，使用的就是第一个  
- logfile ""  
redis默认是将日志输出到/dev/null，也就是不会写到文件。可以设置一个路径保存日志，有时候方便查看问题

################################ SNAPSHOTTING ################################  
- save   
```
save 900 1
save 300 10
save 60 10000
```
格式 save \<second> \<change> 表示多少秒有多少次修改就可以触发生成rdb文件        

- dir ./   
- dbfilename dump.rdb   
rdb生成的目录和文件名称     

- stop-writes-on-bgsave-error yes  
这个参数表示当在保存rdb快照时，如果出错，是否要拒绝写入。什么意思呢，如果reids在保存rdb快照时，需要写硬盘，如果此时硬盘空间不足就会写入失败，那么设置为yes就表示redis也不允许客户端写入，为了保证数据的完整性，但读依然是可以的。如果设置为no，那么依然可以写入redis，但是可能会出现数据不一致的问题

################################## SECURITY 安全 ###################################
- requirepass foobared  
使用密码，默认是不使用，生产环境通常会设置密码

################################### CLIENTS 客户端 ####################################
- maxclients 10000
redis允许客户端同时链接的数量，默认为10000，超过就会报错。  

############################## MEMORY MANAGEMENT 内存 ################################
- maxmemory <bytes>
允许使用的最大内存。如果超过该限制，redis会根据maxmemory-policy策略进行处理。如果不限制就是可以使用本机的最大内存，这个参数和jvm的-xmx是类似的
- maxmemory-policy  
当内存达到最大限制时的处理策略。有8种选择：
  - volatile-lru 在有设置过期时间的key中，使用LRU进行淘汰  
  - allkeys-lru 在所有key中，使用LRU进行淘汰
  - volatile-lfu 在设置过期时间的key中，使用LFU进行淘汰
  - allkeys-lfu 在所有key中，使用LFU进行淘汰
  - volatile-random 在设置过期时间的key中，随机淘汰
  - allkeys-random 在所有key中，随机淘汰
  - volatile-ttl 选择将要过期的key进行淘汰
  - noeviction 不淘汰，对写入请求直接返回错误，也是默认是处理方式

############################## APPEND ONLY MODE 备份 ###############################
- appendonly no  
是否启用aof持久化，默认是否。redis默认是使用rdb的方式持久化数据，rdb是以快照的形式保存数据，rdb的缺点是在redis挂掉的情况下，重启时会丢失一部分的数据，因为快照时隔断时间生成的
- appendfilename "appendonly.aof"    
aof文件的名称   
- appendfsync everysec  
使用aof时的写入方式，默认是每秒一次。设置为always会对每个命令都进行写入，这对数据的保障是最高的，但性能也是最低的   
- auto-aof-rewrite-percentage 100   
当aof文件大小超过上一次的100%时，执行aof重写   
- auto-aof-rewrite-min-size 64mb   
当aof文件大小超过64mb时，执行aof重写   
- aof-use-rdb-preamble yes  
混合使用aof和rdb的持久化方式。rdb和aof各有优缺点，redis4.0后提供了两种模式的混合，综合两种的优点


################################## SLOW LOG 日志 ###################################  
- slowlog-log-slower-than 10000  
记录慢查询，单位是微秒，默认是10毫秒。可以通过redis-cli或者一些监控工具对redis进行监控，找到redis的慢查询请求，因为redis是单线程的，一个慢查询可能就会影响整个系统的性能

############################# EVENT NOTIFICATION 事件 ##############################
- notify-keyspace-events ""  
这个配置不常用，但有时候可以提高逼格。它可以实现发布-订阅的功能，例如当key过期时，client可以得到一个响应事件。这个可以实现一个延迟消息的功能，如设置key的过期时间为1min,那么1min后客户端收到redis server的通知，执行短信通知。

################################ REDIS CLUSTER 集群 ###############################
- cluster-enabled no  
开启集群模式，默认是不开启的  

########################### ACTIVE DEFRAGMENTATION 碎片化内存 #######################
- activedefrag yes   
开启碎片化整理功能，默认是否。  
redis在频繁分配和释放内存，时间久了就会产生碎片化内存，这部分内存可能无法被使用，开启这个功能，reids会基于配置对内存进行整理，提升内存的使用率。  
进行内存整理会影响性能，所以默认情况下，没有开启  
- active-defrag-ignore-bytes 100mb  
当碎片化内存达到100mb时，开始清理
- active-defrag-threshold-lower 10  
当碎片化内存达到10%时，开始清理
- active-defrag-threshold-upper 100  
当碎片化内存达到100%时，开始清理
- active-defrag-cycle-min 5  
清理内存碎片占用 CPU 时间的比例不低于此值，redis进行清理时，需要占用一定的cpu
- active-defrag-cycle-max 75  
清理内存碎片占用 CPU 时间的比例不高于此值，redis进行清理时，不能高于此值，否则会影响使用  

########################### 多线程，redis6.0 #######################
- io-threads 4  
redis6.0开始使用可以使用该参数，在处理网络请求时使用多线程，提升网络IO读写效率。但redis在执行命令时，依然是使用单线程。
- io-threads-do-reads yes  
开启io-threads默认只对write有效，对read无效，redis认为read开启多线程的效果有限，所以默认是不启用。












 
 
 
 
 
 
 

