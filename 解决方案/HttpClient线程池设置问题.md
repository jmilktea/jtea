## 背景
   双十一单量翻两倍多，原有的处理单量6000/h速度处理不过来。即使新增到五个节点，处理速度提升不大。单节点的处理速率慢。
   查看调用方/被调用方的内存、CPU资源非常低。（一度怕被五个节点压垮，被调用方也新增两个节点。）
## 问题原因
使用的httpClient包是: org.apache.httpcomponents

    ```
     if (systemProperties) {
        String s = System.getProperty("http.keepAlive", "true");
        if ("true".equalsIgnoreCase(s)) {
            s = System.getProperty("http.maxConnections", "5");
            final int max = Integer.parseInt(s);
            poolingmgr.setDefaultMaxPerRoute(max);
            poolingmgr.setMaxTotal(2 * max);
        }
    }
    if (maxConnTotal > 0) {
        poolingmgr.setMaxTotal(maxConnTotal);
    }
    if (maxConnPerRoute > 0) {
        poolingmgr.setDefaultMaxPerRoute(maxConnPerRoute);
    }
    ```
   - maxConnTotal参数:连接池中最大连接数。最大链接数是 10.
   - maxConnPerRoute参数:是针对一个域名同时间能使用的最多的连接数，例如：分别请求 www.xx.com和www.xx2.com 的资源那么他就会产生两个route。
    如果没设置默认就是 5
 ## 问题解决
 - 上诉问题慢如蜗牛是因为没有设置 maxConnPerRoute参数。导致同一个域名同一时间使用最多的链接数太少。加大这个参数至合理范围。
 - 由于httpClient外面还嵌套一层线程池（异步调用），与httpClient链接数对称。
 ## 结果
 每个小时最高可以达到六万处理速度。