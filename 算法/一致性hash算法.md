## hash算法  
hash算法在我们开发中非常常见，如java中的HashMap,redis中的hash数据类型，在nginx负载均衡策略中ip_hash可以根据ip hash路由到指定节点，都使用了hash算法。   
hash算法在在分布式系统领域的应用非常广泛，如负载均衡策略、分库分表策略、缓存节点选择等。   

![image](https://github.com/jmilktea/jmilktea/blob/master/%E7%AE%97%E6%B3%95/images/consistent-hash1.png)         
如上图所示，通常我们会对一个key进行hash后取模，即可得到一个下标，通过这个下标即可访问对应的目标。一个好的hash算法计算出来的结果是冲突率是比较低的，这样取模后得到的结果也是相对均匀的，可以让数据较平均的分布。  
hash算法的问题是当增删节点时，已有的数据需要大量的迁移。如下图所示    
![image](https://github.com/jmilktea/jmilktea/blob/master/%E7%AE%97%E6%B3%95/images/consistent-hash2.png)     
如果节点的数据非常多，大量的迁移会给系统带来很大的压力，不仅涉及到数据的搬迁，原本访问不到的数据还需要重定向到正确的位置。假设上述是缓存的场景，就会出现大量的缓存数据的迁移，增加节点后就可能出现大量数据访问不到需要穿透到数据库或者重定向一次访问到新的节点，在数据量大的情况下，这非常消耗性能。     

## 一致性hash算法   
一致性hash算法就是为了解决简单hash算法在增删节点下表现不佳的问题，算法保证在这种情况下，大部分数据还是在原来的位置，只需要迁移很少数的数据，降低对系统的影响。   
一致性hash算法计算公式：hash(key) % 2^32。这样计算出来是一个0至2^32-1的整数，我们把这些整数在一个环上表示，环的每个点就是起始节点是0，终点是2^32-1的整数。     
假设有A,B,C三个节点，我们通过计算后得到的一个整数，落到环上如图：   
![image](https://github.com/jmilktea/jmilktea/blob/master/%E7%AE%97%E6%B3%95/images/consistent-hash3.png)      

对于每个数据也同样计算得到一个值，落在环上，那么怎么决定数据归属于那个节点呢？方式是顺时针找到第一个与它最接近的节点，这就是目标节点。如图data1,data4归属于节点A      
![image](https://github.com/jmilktea/jmilktea/blob/master/%E7%AE%97%E6%B3%95/images/consistent-hash4.png)      

假设现在需要扩充一个节点D,其hash计算值落在如图所示，此时data4顺时针与它最接近的节点是D,不是A了，数据需要迁移，从A迁移到D。但是data1,data2,data3不需要迁移，此时迁移的只是环上一部分数据，并不会大面积的变动，以此可以减少对系统的影响。从这里也可以看到，一致性hash并不是“强一致”，它的结果一样会变，只不过变动得比较小，表现比较稳定。   
![image](https://github.com/jmilktea/jmilktea/blob/master/%E7%AE%97%E6%B3%95/images/consistent-hash5.png)     

上述的效果看起来很不错，这是理想状态。实际上可能出现如下节点偏斜的效果，即节点在环上的距离很接近，导致大部分数据都分布在其中某个节点，这将导致数据分布非常不均匀，有的节点压力很大，有的节点很空闲。   
![image](https://github.com/jmilktea/jmilktea/blob/master/%E7%AE%97%E6%B3%95/images/consistent-hash6.png)      

我们无法保证hash算法每次都能计算出合理的位置，让节点可以很好的分布在hash环上，让数据可以均匀分布，也就是无法同个改进算法来解决偏斜这个问题。    
这种情况我们使用**虚拟节点**来解决，虚拟节点的做法是每个节点虚拟出多个节点，环上的节点越多，数据分布就越均匀，同样计算的成本也会越高。    
![image](https://github.com/jmilktea/jmilktea/blob/master/%E7%AE%97%E6%B3%95/images/consistent-hash7.png)        

## 应用    
一致性hash算法在许多中间件也有应用，如xxljob的调度策略中就有一致性hash，dubbo的负载均衡策略也有一致性hash，接下来我们看下dubbo的一致性hash负载均衡hash算法。   
dubbo的一致性hash负载均衡策略由 ConsistentHashSelector 实现，consistent是一致的意思。其代码如下：   
```
    private static final class ConsistentHashSelector<T> {
        //使用TreeMap实现环结构，Long为其对应的hash环上的值，Invoker为实际对应的节点
        private final TreeMap<Long, Invoker<T>> virtualInvokers;
        //每个节点复制出来的虚拟节点数
        private final int replicaNumber;
        //此ConsistentHashSelector对应的hash值，用于判断此ConsistentHashSelector是否有过期（如果invokers发生了变化，这个值会失效）
        private final int identityHashCode;
        //根据哪些argumentIndex来计算virtualInvokers里面的Long值
        private final int[] argumentIndex;
        
        //构造方法    
        ConsistentHashSelector(List<Invoker<T>> invokers, String methodName, int identityHashCode) {
            this.virtualInvokers = new TreeMap<Long, Invoker<T>>();
            this.identityHashCode = identityHashCode;
            URL url = invokers.get(0).getUrl();
            //虚拟节点数，默认为160个
            this.replicaNumber = url.getMethodParameter(methodName, "hash.nodes", 160);
            //默认通过第一个参数计算hash值
            String[] index = Constants.COMMA_SPLIT_PATTERN.split(url.getMethodParameter(methodName, "hash.arguments", "0"));
            argumentIndex = new int[index.length];
            for (int i = 0; i < index.length; i++) {
                argumentIndex[i] = Integer.parseInt(index[i]);
            }
            for (Invoker<T> invoker : invokers) {
                String address = invoker.getUrl().getAddress();
                for (int i = 0; i < replicaNumber / 4; i++) {
                    byte[] digest = md5(address + i);
                    for (int h = 0; h < 4; h++) {
                        //计算虚拟节点的hash值后，保存到环结构
                        long m = hash(digest, h);                                            
                        virtualInvokers.put(m, invoker);
                    }
                }
            }
        }
    }
``` 
通过上面的代码可以看到ConsistentHashSelector默认每个节点会创建160个虚拟节点，然后计算每个节点的hash值后保存在TreeMap。   
当需要选择节点的时候，调用的是如下方法   
```
        private Invoker<T> selectForKey(long hash) {
            Map.Entry<Long, Invoker<T>> entry = virtualInvokers.tailMap(hash, true).firstEntry();
            if (entry == null) {
                entry = virtualInvokers.firstEntry();
            }
            return entry.getValue();
        }    
```   
这里巧妙的使用了TreeMap数据结构，tailMap会返回第一个大于等于指定值的子集，firstEntry返回了子集的第一个元素，也就是我们要找的顺时针第一个节点。   
tailMap实际是构造出一个子集Map，它并不会去遍历树再保存起来，不然这样就相当于在遍历环，效率太低了，firstEntry时只需要获取第一个元素。   

上面我们提到了缓存也可以应用一致性hash算法，但我们最常用的redis确没有实现它，但是原理是一样的。redis创建了16384个槽，这些槽会分配个集群的每个节点，redis使用crc16(key) % 16384 计算得到hash值，确定对应的key在哪个节点上，当需要增删节点时，需要手动指定要从哪些节点迁移哪些槽位到新节点，例如原来有A,B,C三个节点，分配的槽位分表是1-5000,5000-10000,10000-16384,当新增一个节点时，可以指定从每个槽位迁移1200个左右的槽位到新节点。这是思想和一致性hash算法是一样的，可以任务槽位就是redis虚拟出来的节点，只不过它固定了个数和顺序。当添加节点时，涉及到槽位的迁移，和我们前面说的一样，当客户端访问到不存在的槽位时，会发生重定向，redis会返回MOVE指令，并且告诉客户端应该访问的节点，等迁移完成会通知客户端，后续就按照原来的方式正常访问了。    
