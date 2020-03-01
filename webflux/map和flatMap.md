map和flatMap方法都可以将元素映射成另一种元素，两者的区别是：  
1. map 返回普通类型，而flatMap的是Publisher，也就是Mono或者Flux对象
2. map 的转换是同步的，而flatMap是异步的  
两者的操作过程如图：
![image](https://github.com/jmilktea/jmilktea/blob/master/webflux/images/map.png)  
![image](https://github.com/jmilktea/jmilktea/blob/master/webflux/images/flatmap.png)

可以分别执行如下代码，map会有序输出，而flatMap是无序的
```
    @Test
    public void testMapAndFlatMap() throws InterruptedException {
        Flux.just(1, 2, 3, 4).map(s -> s * 2).subscribe(s -> "map:" + System.out.println(s));
        Flux.range(1, 10).flatMap(s -> Mono.just(s * 2).delayElement(Duration.ofSeconds(1))).subscribe(s -> "flatMap:" + System.out.println(s));
        Thread.sleep(3000);
    }
```
