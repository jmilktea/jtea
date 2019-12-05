jmeter是一个java客户端测试工具，可以对我们的接口进行压测，并提供报告。正常情况下我们的程序按照预期运行，但是在并发量较大的情况下往往会产生一些预想不到的结果。之前在对seata进行测试时就用jmeter进行压测，并发现了bug，详细见[issure]。本章介绍jmeter的使用步骤。

1. [下载](https://jmeter.apache.org/download_jmeter.cgi),解压后，双击ApacheJMeter.jar即可打开jmeter客户端

2. 文件 -> 新建一个测试计划  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/jmeter/images/%E6%96%B0%E5%BB%BA%E6%B5%8B%E8%AF%95%E8%AE%A1%E5%88%92.png)

3. 右键测试计划 -> 添加 -> 线程(用户) -> setup线程组  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/jmeter/images/%E8%AE%BE%E7%BD%AE%E7%BA%BF%E7%A8%8B%E7%BB%84.png)

4. 右键setup线程组 -> 添加 -> 取样器 -> http请求  
填写基本信息，请求地址，端口，路径，参数等    
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/jmeter/images/%E8%AF%B7%E6%B1%82%E5%8F%82%E6%95%B0.png)

5. 如果需要http请求头可以 右键http请求 -> 添加 -> 配置元件 -> http信息头管理   
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/jmeter/images/%E8%AF%B7%E6%B1%82%E5%A4%B4.png)  

6. 运行即可并发访问接口。如果要观察结果，可以 右键http请求 -> 添加 -> 监视器 -> 察看结果树 和 汇总报告  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/jmeter/images/%E8%AF%B7%E6%B1%82%E7%BB%93%E6%9E%9C.png)
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/jmeter/images/%E6%B1%87%E6%80%BB%E6%8A%A5%E5%91%8A.png)
