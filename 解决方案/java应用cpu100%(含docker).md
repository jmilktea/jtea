## 前言
线上应用占用cpu高的问题相信很多朋友都遇到过，可能是由于程序哪里出现bug导致频繁在循环计算，或者是由于应用内存不足导致频繁进行gc。有时候为了快速恢复我们会重启应用，但这属于临时解决，很快问题又会再次出现，所以要根本解决需要知道此时应用正在做什么。本章介绍如何通过jstack找到出现问题的代码，分为两种情况，一种是直接直接部署在宿主机上，一种是部署在docker容器上。

## 普通java程序
1.模拟问题
```
@RestController
@SpringBootApplication
public class HighcpuApplication {

    public static void main(String[] args) {
        SpringApplication.run(HighcpuApplication.class, args);
    }

    @RequestMapping("highcpu")
    public void highcpu() {
        while (true) {
        }
    }

}
```
访问http://192.168.56.101:8080/highcpu

2.使用top命令找到占用高的进程，top命令默认就是按照cpu进行排序，如果要按照内存排序可以按下大写M
![image](https://github.com/jmilktea/jmilktea/blob/master/%E8%A7%A3%E5%86%B3%E6%96%B9%E6%A1%88/images/javacpu100-1.png)

3.找到占用cpu的线程 top -p 1849 -H
![image](https://github.com/jmilktea/jmilktea/blob/master/%E8%A7%A3%E5%86%B3%E6%96%B9%E6%A1%88/images/javacpu100-2.png)

4.将线程id转换为16进制 printf "%x\n" 1863 得到线程的16机制id为747
![image](https://github.com/jmilktea/jmilktea/blob/master/%E8%A7%A3%E5%86%B3%E6%96%B9%E6%A1%88/images/javacpu100-3.png)

5.jstack 1849 > highcpu.log 将进程信息导出，找到747的线程
![image](https://github.com/jmilktea/jmilktea/blob/master/%E8%A7%A3%E5%86%B3%E6%96%B9%E6%A1%88/images/javacpu100-4.png)

## docker部署
现在大部分应用都跑在docker容器，java应用跑在docker上时，解决方式有些不同
1.发布应用到docker容器
由于jdk镜像比较大，所以一般使用的是jre镜像。这就会出现一个问题，jre没有jstack jmap等命令，所以进入容器进行jstack会提示命令不存在。
```
from openjdk:8-jre
copy ./highcpu-0.0.1-SNAPSHOT.jar app.jar
cmd java -jar app.jar
```

2.访问http://192.168.56.101:8080/highcpu，同理让cpu升高

3.找到cpu占用高的进程4125
![image](https://github.com/jmilktea/jmilktea/blob/master/%E8%A7%A3%E5%86%B3%E6%96%B9%E6%A1%88/images/javacpu100-5.png)

4.找到占用cpu的线程 top -p 4125 -H
![image](https://github.com/jmilktea/jmilktea/blob/master/%E8%A7%A3%E5%86%B3%E6%96%B9%E6%A1%88/images/javacpu100-6.png)

这样找到的线程id并不准确，会和docker内的线程对应不上，所以应该执行
docker exec -it 容器id ps -ef 和 docker exec -it 容器id top -p 进程id -H  来找到容器内的线程id  
20转换为16进制即为14
![image](https://github.com/jmilktea/jmilktea/blob/master/%E8%A7%A3%E5%86%B3%E6%96%B9%E6%A1%88/images/javacpu100-7.png)

5.docker logs 341 > highcpu-docker.log 导出进程信息，找到线程id为14的线程
![image](https://github.com/jmilktea/jmilktea/blob/master/%E8%A7%A3%E5%86%B3%E6%96%B9%E6%A1%88/images/javacpu100-8.png)

需要注意的是，由于docker的安全策略，这里如果直接使用jstack 4125 > highcpu.log 会报错，提示
![image](https://github.com/jmilktea/jmilktea/blob/master/%E8%A7%A3%E5%86%B3%E6%96%B9%E6%A1%88/images/javacpu100-9.png)
