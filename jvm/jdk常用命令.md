jdk bin目录下为我们提供了许多实用工具，包括命令行工具，如jps、jinfo、jstack等，还有可视化分析工具jvirsualvm。    
本篇我们就来熟悉一下这些常见的命令行工具，以java8为例，这些命令和说明都可以在oracle官方文档找到：[链接](https://docs.oracle.com/javase/8/docs/technotes/tools/index.html)。   

## jps    
查看jvm进程状态    
- jps：输出虚拟机执行主机名称和虚拟机进程id，面试时有时候会问怎么查看机器上的java进程，除了使用ps -ef | grep xxx linux命令外，也可以使用使用jps命令。  
- jps -q：输出虚拟机进程id   
- jps -l：相比直接jps命令，会输出jar包的路径    
- jps -v：相比jps会输出启动参数，用法：jps -v | grep myapp 可以查看myapp启动时传递给jvm的参数    
- jps -m：相比jps会输出传递给main函数的参数    

## jinfo    
查看jvm进程信息   
- jinfo pid：显示jvm进程的全部属性，包括系统属性和jvm属性，系统属性包括jdk的版本，jre的路径，运行程序的用户等等，jvm属性包含默认的参数以及我们指定的启动参数，在这里也可以看到当前jvm使用的是哪种gc收集器，例如我本地环境的输出-XX:+UseParallelGC，表示新生代使用Paralle Scavenge收集器，老年代使用Parallel Old收集器，这是jdk8默认的设置。   
- jinfo -flags pid：相比jifno pid不会输出系统属性    
- jinfo -flag：还可以不启动服务动态的设置某些jvm参数，但不是所有参数都可以动态设置，这个感觉用处不大。   

## jstat   
查看jvm统计信息，主要是gc相关的    
- jstat -class pid：显示类加载信息   
- jstat -complier pid：显示JIT编译信息   
- jstat -gc pid：显示gc相关信息，以数值的形式   
- jstat -gcutil pid：显示gc相关信息，以百分比的形式   
- jstat -gccapacity pid：显示各代及使用情况
- jstat -gcnew pid：显示新生代信息    
- jstat -gcnewcapacity pid：显示新生代使用情况
- jstat -gcold pid：显示老年代信息   
- jstat -gcoldcapacity pid：显示老年代使用情况   
- jstat -gcmetacapacity pid：显示元数据空间使用情况
- jstat -gccause pid：显示最近gc的原因    

jstat -gc 输出字段含义如下：  
- S0C：survivor0总空间
- S1C：survivor1总空间  
- S0U：survivor0已使用空间
- S1U：survivor1已使用空间   
- EC：eden总空间
- EU：eden已使用空间   
- OC：old总空间
- OU：old已使用空间   
- MC：元数据总空间
- MU：元数据已使用空间
- YGC：程序启动以来发生的young gc次数
- YGCT：程序启动以来发生young gc所消耗的时间
- FGC：程序启动以来发生的full gc次数
- FGCT：程序启动以来发生full gc所消耗的时间
- GCT：程序启动以来发生gc所消耗的时间，包含young/full gc
- CCSC/CCSU：压缩类空间和已使用空间

jstat -gcutil 输出字段含义如下，与-gc基本是对应的
- S0：survivor0已使用百分比
- S1：survivor1已使用百分比
- E：eden已使用百分比
- O：old区已使用百分比
- M：元数据已使用百分比   

jstat -gccapacity 输出字段含义如下： 
- NGCMN：新生代最小容量
- NGCMX：新生代最大容量
- NGC：新生代当前容量 
- OGCMN：老年代最小容量
- OGCMX：老年代最大容量
- OGC/OC：老年代当前容量
- MCMN：元数据最小容量
- MCMX：元数据最大容量
- MC：元数据当前容量   

jstat输出的是结果信息，是不会变化的，有时候我们为了观察，可以使用如下命令每500ms输出一次结果，观察20次   
```
jstat -gc pid 500 20
```

## jstack   
查询jvm运行栈信息   
- jstack pid：打印jvm进程栈信息   
- jstack -F pid：强制打印栈信息   
- jstack -m pid：打印包含java和native c/c++的栈信息   
- jstack -l pid：打印关于锁的其它信息    

jstack可以让我们看到当前jvm正处运行的线程和状态。例如现在pid进程占用cpu很高，那我们可以使用top命令看是该进程的哪个线程占用cpu很高   
```
top -p pid -H
```
知道是哪个线程了，就可以通过线程id看线程当前的堆栈，知道线程正在干什么。  
```
jstack pid | grep threadid
```
通常我们常通过jstack pid > pid-jstack.log 将运行栈导出，保存现场，再通过一些工具进行分析。     

## jmap  
查看jvm运行堆信息    
- jmap -heap pid：显示堆概要信息，包括使用哪种gc，堆配置信息，已经堆各区域使用情况   
- jmap -dump:format=b,file=./highmem.hprof pid：导出jvm堆信息，然后可以利用jvisualvm等工具分析。需要注意的是，如果程序占用空间很大，dump过程会很久    
- jmap -histo[:live] pid：显示堆中对象统计信息，live参数表示只统计存活的

```
jmap -histo:live pid | head -10
```  
该命令只显示当前堆排名前10的对象信息，其中输出[C表示char数组，[B表示byte数组,[I表示int数组     
需要注意的是，jmap -dump,-histo 都会触发fullgc。    
参考：[一次内存泄漏排查](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/%E4%B8%80%E6%AC%A1%E5%86%85%E5%AD%98%E6%B3%84%E6%BC%8F%E6%8E%92%E6%9F%A5.md)    
