jmeter是一个java客户端测试工具，可以对我们的接口进行压测，并提供报告。正常情况下我们的程序按照预期运行，但是在并发量较大的情况下往往会产生一些预想不到的结果。之前在对seata进行测试时就用jmeter进行压测，并发现了bug，详细见[issure]。本章介绍jmeter的使用步骤。

1. [下载](https://jmeter.apache.org/download_jmeter.cgi),解压后，双击ApacheJMeter.jar即可打开jmeter客户端

2. 文件 -> 新建一个测试计划  
![image]()

3. 右键测试计划 -> 添加 -> 线程(用户) -> setup线程组  
![image]()

4. 右键setup线程组 -> 添加 -> 取样器 -> http请求  
填写基本信息，请求地址，端口，路径，参数等    
![image]()

5. 如果需要http请求头可以 右键http请求 -> 添加 -> 配置元件 -> http信息头管理   
![image]()  

6. 运行即可并发访问接口。如果要观察结果，可以 右键http请求 -> 添加 -> 监视器 -> 察看结果树 和 汇总报告  
![image]()