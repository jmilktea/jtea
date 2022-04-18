## 简介
curl是一个利用URL语法在命令行下工作的文件传输工具，通常在linux服务器并没有可视化工具使用，有时候要发请求测试接口就不是那么方便，curl就在这个时候发挥作用，允许我们通过命令发送请求，方便测试，调试。

## 参数
最简单的例子
```
curl www.baidu.com 
```
-v可以显示请求的详细信息，如请求头，响应头  
```
curl www.baidu.com -v
```
-X可以指定请求类型，如POST
```
curl -X POST www.baidu.com -v
```
-H可以指定请求头，在调试时经常需要携带一些请求头信息
```
curl www.baidu.com -H "id:1" -H "name:zhangsan" -v
```
-d可以指定请求参数
```
curl -X POST -d 'id=1' www.baidu.com -v
```
还可以指定从文件读取参数  
```
curl -X POST -d @file.txt www.baidu.com -v
```
完整例子
```
curl -X POST \
  http://test/api/send \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -H 'token: authtoken' \
  -d 'userid=1' \
  -v
```

## postman
实际情况可能参数比较长，命令就会比较难打，我们可以在postman把请求写出，然后导出curl命令  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/curl/images/code.png)  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%B7%A5%E5%85%B7%E7%B1%BB/curl/images/curl.png)

## -w 参数   
-w 参数可以打印请求的一些重要参数，如：   
- time_namelookup：DNS解析所消耗的时间    
- time_connect：连接时间，建立TCP所消耗的实际，包括DNS解析的时间   
- time_pretransfer：准备传输时间，从开始到准备发送第一个字节所消耗的时间   
- time_starttransfer：开始传输时间，从发出请求到接收到第一个字节所消耗的时间   
- time_total：总时间 
- size_request：请求大小   
示例   
```
curl -w "time_connect: %{time_connect}\ntime_starttransfer: %{time_starttransfer}\ntime_nslookup:%{time_namelookup}\ntime_total: %{time_total}\n" baidu.com
```   
在postman中点击响应的时间【Time】，也可以显示出请求详细的消耗时间。    
