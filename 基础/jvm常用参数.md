参数 | 解释
---|---
**内存类**|
-Xss: | 指定线程栈大小，默认是1M  
-Xms: | 指定jvm初始堆大小  
-Xmx: | 指定jvm最大可用内存  
-Xmn：| 设置新生代内存大小
-XX:MetaspaceSize | 元数据空间初始大小（默认为21m）  
-XX:MaxMetaspaceSize | 元数据空间最大大小  
-XX:MaxDirectMemorySize | 最大堆外内存  
**gc类**|
-XX:+PrintGCDetails | 打印gc日志  
-XX:+PrintGCDateStamps | 打印gc时间  
-Xloggc | gc路径
-XX:MaxGCPauseMills | gc最大停顿时间，默认是200ms，jvm会根据这个时间调整堆大小，让gc时间尽可能在这个时间内
-XX:+UseG1GC | 使用g1回收器
-XX:ParallelGCThreads | 并行gc时使用的线程数，默认是2  
-XX:+ParallelRefProcEnabled | 并行处理Reference对象，默认是false，应开启
-XX:NewRatio | 老年代:新生代，默认值是 2:1
-XX:SurvivorRatio | 两个survivor:eden，默认值是2:8
-XX:MaxTenuringThreshold | 默认值是15，进入老年代的年龄
-XX:+UseCMSCompactAtFullCollection | 每次 full gc后对内存进行压缩，可以避免内存碎片问题（内存碎片问题没有了，停顿时间变长了）
-XX:CMSFullGCsBeforeCompaction | 多少次full gc后才对老年代进行压缩
-XX:+UseCMSInitiatingOccupancyOnly | 使用设定的阈值开始cms gc
-XX:CMSInitiatingOccupancyFraction | 达到这个百分比即开始cms gc（由于是并发标记，所以需预留内存）
-XX:+DisableExplicitGC | 明确禁止使用system.gc  
**其它**|
-XX:+HeapDumpOnOutOfMemoryError | 开启oom head dump  
-XX:HeapDumpPath|dump文件路径  
-XX:ErrorFile|jvm crash 路径  
-XX:+UseStringDeduplication | 开启使用重复的string，相同string使用同一份内存，多余的可以回收  
**gc工具**|
[perfma](https://opts.console.perfma.com/) | 参数查询，解释，优化  
[fastthread](https://fastthread.io),[gceasy](https://gceasy.io/)|在线，文件大了上传很慢
jvisualvm+visualGC插件|
mat|
