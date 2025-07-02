jdk bin目录下为我们提供了许多实用工具，包括命令行工具，如jps、jinfo、jstack等，还有可视化分析工具jvirsualvm。    
本篇我们就来熟悉一下这些常见的命令行工具，以java8为例，这些命令和说明都可以在oracle官方文档找到：[链接](https://docs.oracle.com/javase/8/docs/technotes/tools/index.html)。   

## jar
- jar tf yourfile.jar：列出jar包内的文件
- jar xf yourfile.jar path/to/your/file：解压jar包内某个文件
- unzip -p yourfile.jar path/to/your/file：查看jar包内某个文件，不用解压

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
- jinfo -flag：查看jvm参数，如:jinfo -flag UseG1GC pid 查看UseG1GC参数的设置

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

**gdb gcore**   
有时候会遇到java进程“假死”已经无法响应jmap命令，会提示“unable to open socket file”，且让我们使用-F参数来导出堆栈。   
使用-F参数,jmap会使用Linux的ptrace机制来导出堆内存，ptrace是Linux平台的一种调试机制，像strace、gdb都是基于它开发的，它使得调试进程(jmap)可以直接读取被调试进程(jvm)的原生内存，然后jmap再根据jvm的内存布局规范，将原生内存转换为hprof格式。    
但实际操作过程会发现特别慢，因为ptrace每次只能读取少量的字节，对于堆比较大的进程，可能要花费几个小时。    
解决方式是使用：gdb的gcore命令，它可用来生成程序原生内存的core文件，然后jstack、jmap等都可以读取此类文件，导出堆信息后，就可以像原来一样使用工具进行分析了。   
```
# 生成core文件，5709是进程号
$ gcore -o core 5709

# 从core文件中读取线程栈
$ jstack `which java` core.5709

# 将core文件转换为hprof文件，很慢，建议摘流量后执行
$ jmap -dump:format=b,file=heap.hprof `which java` core.5709
```

**arthas**    
笔者还遇到一种情况是，开始可以执行jstack,jmap，但当服务运行一段时间后（超过10天），再执行时就会报错，重启再执行又是正常的。    
似乎跟服务运行时长久不重启有关系，有些文件被操作系统清掉导致执行失败？网上有部分说了是这个问题，不过我看相关路径文件还是存在的，具体原因还没答案。    
跟上面一样，也是提示使用-F，-F会导致jstack丢失一些信息，jmap执行起来非常慢。    
我的解决方式是使用arthas，很奇怪吧，java命令执行失败，但使用arthas是可以的，按理说arthas也是执行jstack,jmap，不过笔者验证过是可以成功的。   
```
thread -10 -- 查看top 10 cpu
thread --all -- 显示所有线程
heapdump arthas-output/dump.hprof -- head dump
```
20250702补充：    
服务运行一段时间后执行jstack,jmap失败的原因是jvm进程启动后会在/tmp目录下创建进程文件，jstack相关命令依赖这个进程文件进行attach，若太久没访问会被linux系统清理掉。    
可以通过命令查看临时文件是否存在，如下，1是进程号：   
```
ls -l /tmp/.java_pid1
```
linux这个机制是配置在/usr/lib/tmpfiles.d/tmp.conf，默认是10天。     


## jcmd
jdk7后新增的一个多功能命令行工具，上面的命令很多功能都可以用jcmd代替
- jcmd -l：查看所有jvm进程，相当于jps -l
- jcmd pid GC.class_histogram：查看类统计信息，相当于jmap -histo
- jcmd pid Thread.print：查看线程信息，相当于jstack 
- jcmd pid GC.heap_dump ./highmem.hprof：导出jvm堆信息，相当于jmap -dump
- jcmd pid GC.run：执行一次System.gc()
- jcmd pid VM.version：查看jvm版本
- jcmd pid VM.native_memory：查看jvm内存信息

```
jcmd 23602 VM.native_memory scale=MB
```
[Native Memory Tracking](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/nmt-8.html)，可以查看jvm进程内存跟踪，可以查看堆、栈、class、代码缓存、gc、常量池等内存占用信息。   
启动命令需要加上：-XX:NativeMemoryTracking=[summary | detail]，开启后会有5%-10%的性能损失，生产环境使用时需注意。     
有时候我们会发现java进程实际占用的内存要比设置的堆内存要高很多，这是因为除了堆java进程还有很多占用内存的地方，NMT就可以帮助我们分析，如下示例输出：
```
Native Memory Tracking:

Total: reserved=1787MB, committed=588MB
-                 Java Heap (reserved=256MB, committed=256MB)
                            (mmap: reserved=256MB, committed=256MB) 
 
-                     Class (reserved=1147MB, committed=138MB)
                            (classes #22418)
                            (malloc=9MB #43687) 
                            (mmap: reserved=1138MB, committed=129MB) 
 
-                    Thread (reserved=45MB, committed=45MB)
                            (thread #148)
                            (stack: reserved=44MB, committed=44MB)
 
-                      Code (reserved=255MB, committed=65MB)
                            (malloc=11MB #16115) 
                            (mmap: reserved=244MB, committed=53MB) 
 
-                        GC (reserved=15MB, committed=15MB)
                            (malloc=6MB #396) 
                            (mmap: reserved=9MB, committed=9MB) 
 
-                  Compiler (reserved=1MB, committed=1MB)
                            (malloc=1MB #1950) 
 
-                  Internal (reserved=35MB, committed=35MB)
                            (malloc=35MB #34550) 
 
-                    Symbol (reserved=28MB, committed=28MB)
                            (malloc=26MB #264859) 
                            (arena=3MB #1)
 
-    Native Memory Tracking (reserved=6MB, committed=6MB)
                            (tracking overhead=6MB)

```
