## 简介  
协议是双方进行有效沟通的一个基础，通过一系列约定，理解对方的意图。常见的协议如tcp,udp,http等，http协议就规定客户端和服务端交互的标准，如请求需要有请求头，请求内容等。对于redis来说，客户端和redis server的交互协议称为RESP（REdis Serialization Protocol）。   
resp协议有如下特点：
- 简单的实现
- 快速地被计算机解析
- 简单得可以能被人工解析  
总结的说就是：简单，快速解析。我们看下例子：  
```
*3
$3
SET
$3
key
$5
hello
```
这就是set key hello 命令的协议，每一行都有一个\r\n作为结尾。
```
+OK
```
这是redis server收到set key hello 命令后给客户端的响应，表示命令执行成功。redis server用不同的回复类型回复命令：
- 用单行回复，回复的第一个字节将是“+”
- 错误消息，回复的第一个字节将是“-”
- 整型数字，回复的第一个字节将是“:”
- 批量回复，回复的第一个字节将是“$”
- 多个批量回复，回复的第一个字节将是“*”
如下：-表示错误，MOVED表示一个重定向，下面的响应会出现在向redis集群，当需要操作的数据再其它server上时。
```
-MOVED 192.168.56.102:6379 
```

## 测试  
我们通过简单代码来观察协议
server 端，开启16379监听，看看客户端发了什么过来
```
ServerSocket serverSocket = new ServerSocket(16379);
Socket socket = serverSocket.accept();
InputStream inputStream = socket.getInputStream();
byte[] arr = new byte[1024];
int len;
while ((len = inputStream.read(arr)) != -1) {
	String message = new String(arr, 0, len, Charset.forName("UTF-8"));
	System.out.println(message);
}
```
接着用jedis客户端，给server发个命令
```
Jedis jedis = new Jedis("localhost", 16379);
jedis.set("key", "hello");
```
观察server输出如下：
```
*3
$3
SET
$3
key
$5
hello
```

以上观察了客户端发的内容，我们可以看下服务端发了什么。创建一个客户端如下：
```
Socket socket = new Socket("redis ip", 6379);
OutputStream outputStream = socket.getOutputStream();
outputStream.write("*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nhello\r\n".getBytes(Charset.forName("UTF-8")));
outputStream.flush();
InputStream inputStream = socket.getInputStream();
byte[] arr = new byte[1024];
int len = inputStream.read(arr);
String message = new String(arr, 0, len, Charset.forName("UTF-8"));
System.out.println(message);
```
按照协议给redis发了一串内容，执行后可以看到返回了+OK 表示成功，那串字符被redis server正确理解，到redis查看也生成了key。  

参考  
[redis协议说明](http://www.redis.cn/topics/protocol.html)  
[5分钟深入Redis之RESP协议](https://mp.weixin.qq.com/s?__biz=MzUzOTY4NjQyMQ==&mid=2247483707&idx=1&sn=c9249dd7cd6de2861eecc72206bc79a2&chksm=fac5e241cdb26b57f860ead9eb062f728293c2e32766ea9d6c35259e99a852a654ac4a485fc5&mpshare=1&srcid=0611H54eXLatfhA5m44w2UcG&sharer_sharetime=1591837790073&sharer_shareid=548db259b62bf54e9bd9577cfeb9791e&from=singlemessage&scene=1&subscene=10000&clicktime=1591863559&enterid=1591863559&ascene=1&devicetype=android-29&version=27000f3b&nettype=WIFI&abtest_cookie=AAACAA%3D%3D&lang=zh_CN&exportkey=A%2B860LL6b56SMjSGFdfuN1s%3D&pass_ticket=HqDnASAW4PlYP8QIDTkQtjoFHdiFkRZH0wCL6mQCK84aEDF77vokau6U2Bpgso%2Fr&wx_header=1)  