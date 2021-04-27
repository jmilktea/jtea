## 前言
根据地理位置查找附近的人、商家或者物品是个常见的场景，比如微信附近的人，美团附近的商店，购物网站选择按距离排序，还有共享单车可以看到附近哪里有空闲的单车等。  
这类场景都是根据当前位置找附近的目标，如图：  
![image](https://github.com/jmilktea/jmilktea/blob/master/redis/images/redis-geo-0.png)  

这里有两个重要条件：  

1.当前位置  
我们地理课本学过，地球上的位置可以通过经度和纬度标记，相当于有一个地理坐标:(经度,纬度)，它可以用来标记地球上每一个位置     
经度是地球上一个地点离一根被称为本初子午线的南北方向走线以东或以西的度数，本初子午线的经度是0°，地球上其它地点的经度是向东到180°或向西到180°，东经正数，西经为负数。  
纬度是指过椭球面上某点作法线，该点法线与赤道平面的线面角，其数值在-90至90度之间，位于赤道以北的点的纬度叫北纬，记为，位于赤道以南的点的纬度称南纬，记为S，北纬为正数，南纬为负数。  
比如笔者现在所在的位置就是：(22.53210059015035, 113.93922242698886)，这个可以在[google地图](https://www.google.com/maps/@22.5321105,113.9392868,16z?hl=zh-cn)获得。  

2.附近   
附近的意思是以当前位置为圆心，半径为r所画出来的圆形区域，这个区域内的目标就是我们要找目标   
那么我们如何计算圆心和目标的距离呢？也就是两个经纬度标记点之间的距离   

**地理空间距离计算**   
我们知道平面上的两个点之间的距离就是直线的距离，而地球是一个球面，两个点的最小距离是弧长。目前计算方式主要有两种：  
1.球面模型，也就是把地球当做是一个标准球体，这种方式计算起来比较简单，但是存在误差，通常在普通场景下都可以使用这种方式  
2.椭球模型，这种方式最接近地球的形状，计算出来的结果比较准确，但是计算过程比较复杂  

我们看基于球面的计算方式：  
![image](https://github.com/jmilktea/jmilktea/blob/master/redis/images/redis-geo-7.png)  

如图：A,B，我们可以将地球看成圆球，假设地球上有A(ja,wa)，B(jb,wb)两点（ja和jb分别是A和B的经度，wa和wb分别是A和B的纬度），A和B两点的球面距离就是AB的弧长，AB弧长=R*角AOB（注：角AOB是A跟B的夹角，O是地球的球心，R是地球半径，约为6367000米）。如何求出角AOB呢？可以先求AOB的最大边AB的长度，再根据余弦定律可以求夹角。整个推导过程有兴趣的可以查看具体资料，实际开发中已经有封装好的类库供我们计算使用。  

![image](https://github.com/jmilktea/jmilktea/blob/master/redis/images/redis-geo-1.png)  
如图我们取一个附近的位置，两点的坐标分别是(22.532265023491117, 113.93928847821488)和(22.538459349954508, 113.92095965822314)，java中我们可以使用[spatial4j](https://github.com/locationtech/spatial4j)，这是一个地理空间计算库，拥有丰富的api。
```
double distance = spatialContext.calcDistance(spatialContext.makePoint(Double.valueOf("113.93928847821488"), Double.valueOf("22.532265023491117")),
      spatialContext.makePoint(Double.valueOf("113.92095965822314"), Double.valueOf("22.538459349954508"))) * DistanceUtils.DEG_TO_KM;
```  
![image](https://github.com/jmilktea/jmilktea/blob/master/redis/images/redis-geo-2.png)
有圆心和半径，我们可以计算这个范围的经纬度范围，也就是如图的矩形区域，可以计算出它的最小/最大经纬度。从图可以看到这样计算会有一个误差，就是上下左右四个夹角实际是不再r范围内的，当r=10km的，这个范围大概是2km的距离，可以三角形边长简单计算出来。实际过程中我们可以先找到这些点，然后再遍历计算距离，超过r的就去掉即可。spatial4j同样提供了实现：
```
SpatialContext spatialContext = SpatialContext.GEO;
//获取r=10km的外接正方形
Rectangle rectangle = spatialContext.getDistCalc().calcBoxByDistFromPt(spatialContext.makePoint(Double.valueOf("113.93925334232964"), Double.valueOf("22.53215096894214")),
            10 * DistanceUtils.KM_TO_DEG, spatialContext, null);
System.out.println("minX:" + rectangle.getMinX());
System.out.println("maxX:" + rectangle.getMaxX());
System.out.println("minY:" + rectangle.getMinY());
System.out.println("maxY:" + rectangle.getMaxY());
```
这是基于java代码的实现，如果我们数据库存储了商家位置的经纬度，通过用户的gps定位，就可以很方便计算出用户与商家的距离。redis同样提供了实现方式，它是基于geohash算法实现的。  

## geohash算法

[geohash](https://en.wikipedia.org/wiki/Geohash)算法是一种地址编码方法，它能够把二维的空间经纬度数据编码成一个字符串。http://www.geohash.cn/ 这里每次点击都会生成一个区域，每个区域都会有一个编号。区域内的所有经纬度都有相同的编号，如下是一个9宫格区域  
![image](https://github.com/jmilktea/jmilktea/blob/master/redis/images/redis-geo-3.png)  

geohash的思想是用二分的方式给经纬度区间标记0和1，比如这样一个点（39.923201, 116.390705）
纬度的范围是（-90，90），其中间值为0。对于纬度39.923201，在区间（0，90）中，因此得到一个1；（0，90）区间的中间值为45度，纬度39.923201小于45，因此得到一个0，依次计算下去，即可得到纬度的二进制表示，如下表：  
![image](https://github.com/jmilktea/jmilktea/blob/master/redis/images/redis-geo-4.png)  

最后得到纬度的二进制表示为：  10111000110001111001  
同理可以得到经度116.390705的二进制表示为：  11010010110001000100  
接下来合并经纬度，经度占偶数位，纬度占奇数位，得到：11100 11101 00100 01111 00000 01101 01011 00001

接下来进行Base32编码，Base32编码表的其中一种如下，是用0-9、b-z（去掉a, i, l, o）这32个字母进行编码。具体操作是先将上一步得到的合并后二进制转换为10进制数据，然后对应生成Base32码。需要注意的是，将5个二进制位转换成一个base32码。如11100转换为10进制就是28，根据映射找到：w。 上例最终得到的值为：wx4g0ec1  
![image](https://github.com/jmilktea/jmilktea/blob/master/redis/images/redis-geo-5.png)   

**突变和误差**  
geohash依赖于其实现的算法，可能存在突变的情况，也就是两个字符串很接近，但是距离相差非常远。
当然它也是有误差的，最终得到的编码字符串长度越长，误差就越小。另外大的区域会覆盖小的区域，如wx4g0ec1包含在wx4g0之内。其误差范围如下：  
![image](https://github.com/jmilktea/jmilktea/blob/master/redis/images/redis-geo-6.png) 

**geohash生成**  
Spatial4J 同样提供了实现，如下：
```
GeohashUtils.encodeLatLon(Double.valueOf("22.53215096894214"),Double.valueOf("113.93925334232964"))
```
**边界问题**  
从上图可以看到，WX4G0的左边就是WX4EP，这可能出现明明两点离得很近，但是算出来的编码不一样，导致查不到数据。解决方案也很简单，就是每次查找，把周边8个区域的编码也找出来，再进行对比。
```
GeoHash geoHash = GeoHash.withCharacterPrecision(lat, lng, len); 
//获取周边8个方位的geoHash码 
GeoHash[] adjacent = geoHash.getAdjacent(); 
```

geohash的应用非常广泛,redis,mysql都对其提供了实现。

## redis geo   
redis geo是基于geo hash算法的实现，主要命令有：
- geoadd 
GEOADD key longitude latitude member [longitude latitude member ...]   
添加一个指定名称的经纬度到key，时间复杂度 O（log（N））  
- geopos  
GEOPOS key member [member ...]   
获取指定名称的经纬度，时间复杂度 O（log（N））
- georadius  
GEORADIUS key longitude latitude radius m|km|ft|mi [WITHCOORD] [WITHDIST] [WITHHASH] [COUNT count] [ASC|DESC] [STORE key] [STOREDIST key]  
 获取指定经纬度，半径范围内的数据，时间复杂度O（N + log（M））
- geohash  
GEIHASH key member   
获取指定对象的geohash，redis使用的是12位，时间复杂度O(log(N))
- geodist  
GEODIST key member1 member2 [unit]  
 计算两个对象间的距离，时间复杂度O(log(N))
- georadiusbymember  
GEORADIUSBYMEMBER key member radius m|km|ft|mi [WITHCOORD] [WITHDIST] [WITHHASH] [COUNT count]   
这个命令和georadius类似，计算指定对象，半径范围内的数据，时间复杂度：O(N+log(M)) 

详细参数释义可以参考：http://www.redis.cn/commands.html   
有几个点需要注意
- 移除元素  
redis geo的底层数据结果是zset，zset是一个有序集合。geo没有提供删除命令，可以使用zset的zrem移除集合元素。
- 性能问题  
集合数量越大，geo命令的延迟会越高，由于redis命令是当线程的，可能会阻塞其它命令的执行。本地测试集合元素10w个，使用georadius求得半径10km附近100个点，大概需要40ms。在实际生产中可以通过设计多个key来分散集合的数量，通过多次查询降低redis查询的延迟时间。  
- 误差  
geohash算法是有误差的，redis geo同样存在误差，最差的情况下这个数值为0.5%，对于一些要求严格的场景需要注意这点。

我们使用springboot RedisTemplate操作相关api，如下：
```
@Test
public void test() {
   //从主题公园（-6.334310912242693, 106.84875768873876）当前位置
   int pointCount = 100_000;
   long useMaxTime = 0L;
   int over100MsCount = 0;
   SpatialContext spatialContext = SpatialContext.GEO;
   //取10km附近的点
   Rectangle rectangle = spatialContext.getDistCalc().calcBoxByDistFromPt(spatialContext.makePoint(Double.valueOf("106.84875768873876"), Double.valueOf("-6.334310912242693")),
         10 * DistanceUtils.KM_TO_DEG, spatialContext, null);
   for (int i = 1; i <= pointCount; i++) {
       //随机生成经纬度
      double d1 = RandomUtils.nextDouble(rectangle.getMinX(), rectangle.getMaxX());
      double d2 = 0 - RandomUtils.nextDouble(Math.abs(rectangle.getMaxY()), Math.abs(rectangle.getMinY()));
      Point point = new Point(d1, d2);      
      //geo add
      redisTemplate.opsForGeo().add("geo_key", point, i);     
   }
}
```
计算范围
```
@Test
public void testSearch() {
   Point point = new Point(106.84875768873876, -6.334310912242693);
   for (int i = 2; i <= 10; i += 2) {    
      //每次查i * 20个
      Circle circle = new Circle(point, new Distance(i, Metrics.KILOMETERS));
      RedisGeoCommands.GeoRadiusCommandArgs geoRadiusCommandArgs = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
            .includeCoordinates() //返回坐标
            .includeDistance() //返回距离
            .sortAscending() //从近到远
            .limit(20 * i);//查询数量
      GeoResults geoResults = redisTemplate.opsForGeo().radius("geo_key", circle, geoRadiusCommandArgs);      
      System.out.println("count:" + geoResults.getContent().size() + ",avg distance:" + geoResults.getAverageDistance().getValue());
   }
}
```


