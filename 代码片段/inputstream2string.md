## 简介
InputStream转String，例如在调试feign的时候可能会用到，用来查看body的内容。需要注意的是流顾名思义，只能读取一次，读完就没有了。这里用apache-commons-io和jdk的方式实现，更多方式参考[这篇文章](https://mp.weixin.qq.com/s?__biz=MzIwMzY1OTU1NQ==&mid=2247488034&idx=1&sn=4267551ecd4cb2961116c76e38e64d48&chksm=96cd526ea1badb7883926049b535bd40f33a7dc55c8b512ca97b6a223175d456b11cb4180ed0&scene=90&xtrack=1&subscene=93&clicktime=1577326066&enterid=1577326066&ascene=56&devicetype=android-29&version=27000935&nettype=cmnet&abtest_cookie=AAACAA%3D%3D&lang=zh_CN&exportkey=A%2BwZ3ysikSIfj5PTB9I%2FQnA%3D&pass_ticket=qEqh73WsCz3%2Bx9NYBkwzzOpBcThPlgQmJbNukzj9Mt37nzfKAUa2TUDdwUxImtR0&wx_header=1)
## 代码
```
<dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
    <version>2.6</version>
</dependency>

@Test
public void testInputStream2String() throws IOException {
    String data = "hello world";
    InputStream inputStream = new ByteArrayInputStream(data.getBytes());
    System.out.println(IOUtils.toString(inputStream, Charset.forName("UTF-8")));

    //流只能读取一次，读完就没了
    inputStream = new ByteArrayInputStream(data.getBytes());
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    byte[] bytes = new byte[1024];
    while (inputStream.read(bytes) != -1) {
        outputStream.write(bytes);
    }
    System.out.println(outputStream.toString("UTF-8"));
}
```