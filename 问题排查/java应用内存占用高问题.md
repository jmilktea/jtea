## 前言
线上应用有时候会出现内存占用高居不下的情况，这会导致一系列问题。如果应用有通过-Xmx限制最大可用内存，或者docker通过-m限制容器可用内存，那么内存达到最大内存时，再请求分配就会失败，报OOM(OutOfMemoryError)，但不会影响宿主机上的其它应用。如果没有限制，那么会影响整个服务器的所有程序。
需要注意的是，内存占用过高还会影响整个系统的负载，因为当内存不足时，jvm首先要做的是尝试进行gc回收内存，gc会占用cpu资源并且stop the world。这个问题通常这也是由于程序有问题导致，重启应用后面还是会再次出现，我们一样需要找到出现问题的代码位置。

## jmap
1. 通过一段简单的代码模拟，通过不断访问/highmem内存会不断升高，最终报错
```
@RestController
@SpringBootApplication
public class HighmemApplication {

    private static List<String> list = new ArrayList<>();

    public static void main(String[] args) {
        SpringApplication.run(HighmemApplication.class, args);
    }

    @RequestMapping("highmem")
    public Integer highmem() {
        String[] strs = new String[10000000];
        list.addAll(Arrays.stream(strs).collect(Collectors.toList()));
        return list.size();
    }
}
```
多次访问后出现OOM
![image](https://github.com/jmilktea/jmilktea/blob/master/%E9%97%AE%E9%A2%98%E6%80%BB%E7%BB%93/images/javamem-1.png)

2. 找到进程
通过 top(按M) 按照内存排序，可以找到占用内存高的进程id
![image](https://github.com/jmilktea/jmilktea/blob/master/%E9%97%AE%E9%A2%98%E6%80%BB%E7%BB%93/images/javamem-2.png)

3. 通过jmap dump出进程信息，dump出的日志比较大，而且需要一点时间，这段时间jvm会停止对外服务，正式环境执行需谨慎
jmap -dump:format=b,file=./highmem.hprof 进程id

4. 使用jvisualvm工具对dump文件进行分析，jvisualvm是jdk提供的可视化工具，在jdk的bin目录下可以找到。jvisualvm将high.mem.hprof装入后，可以查找最大的几个对象，如图可以看到ArrayList非常大
![image](https://github.com/jmilktea/jmilktea/blob/master/%E9%97%AE%E9%A2%98%E6%80%BB%E7%BB%93/images/javamem-3.png)
![image](https://github.com/jmilktea/jmilktea/blob/master/%E9%97%AE%E9%A2%98%E6%80%BB%E7%BB%93/images/javamem-4.png)


## 扩展
1. 上面的做法是在事故过程中进行处理，我们还可以提前进行准备，设置jvm参数：-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=highmemdump.hprof
这两个参数分别表示：开启OOM进程dump和dump文件的路径，当jvm出现OOM时，jvm就会自动dump出进程快照。当然jmap还是有意义的，有时候内存泄漏会导致内存一直占用很高，但不一定会出现OOM，这个时候还是要通过jmap来分析。
2. 上面提到jmap时jvm会停止对外服务，如果此时有请求到来就会一致阻塞，直到超时或者jmap完成。人生病了就得请假不上班，节点出问题了，就要切断到它这里的请求，也就是该节点要下线，这就涉及到另外一个问题，如何平滑的下线服务？以spring cloud为例，服务下线就是给它标记一个停止状态，等该状态同步到客户端和网关时，就会把该服务剔除，不再路由到它。如使用nacos配置中心，在nacos控制台，可以直接控制节点的上下线。
![image](https://github.com/jmilktea/jmilktea/blob/master/%E9%97%AE%E9%A2%98%E6%80%BB%E7%BB%93/images/javamem-5.png)
