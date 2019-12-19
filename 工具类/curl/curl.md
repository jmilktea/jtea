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
![image]() 