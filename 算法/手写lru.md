LRU(least recently used)最近最少使用算法，是一种常见的页面置换算法，或缓存淘汰算法，在许多系统中都可以见到它的身影，例如linux内存管理，redis缓存淘汰算法，mysql buffer pool缓存淘汰等都是基于LRU或者它的改进实现。同时LRU算法也是面试中非常常见的题目，本篇我们来手写一个LRU。    

我们先看下LRU算法规则的定义，首先资源是有限的，所以在达到一定容量的时候，我们就需要清理那些不常用的资源，以腾出空间来使用。    
最近最少使用的意思就是如果一个资源被使用（包括添加，修改，查询），那么它就应该被标记为最近使用过，也就是它的位置要改变。    
如下图所示，我们依次添加1,2,3,4,5五个元素，越往后表示最近有使用，当再次访问1时，需要将1添加到5后面，表示1最近使用。   
![image]()    

**基于LinkedHashMap**    
实际在java中LinkedHashMap已经对LRU进行实现，我们看下源码的备注   
```
* <p>A special {@link #LinkedHashMap(int,float,boolean) constructor} is
 * provided to create a linked hash map whose order of iteration is the order
 * in which its entries were last accessed, from least-recently accessed to
 * most-recently (<i>access-order</i>).  This kind of map is well-suited to
 * building LRU caches
```
LinkedHashMap是有序的hashmap，其中它有一个构造方法提供了LRU的实现，我们也指定最后一个参数accessOrder为true，那么在每次访问元素后，LinkedHashMap就会调整顺序。    
但有一个问题是，hashmap是会扩容的，我们没有办法指定最大容量，这会出现一直往里面添加元素，但不会删除。LinkedHashMap中有一个removeEldestEntry方法，表示是否移除最早的元素，我们可以判断当集合数量达到容量时就触发该操作。实现如下：
```
public class LRULinkedHashMap<K, V> extends LinkedHashMap<K, V> {

	private int capacity;

	public LRULinkedHashMap(int capacity) {
		super(capacity, 0.75f, true);
		this.capacity = capacity;
	}

	@Override
	protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
		boolean b = size() > capacity;
		System.out.println("removeEldestEntry:" + b);
		return b;
	}
}
```
我们看下它的get方法
```
    public V get(Object key) {
        Node<K,V> e;
        if ((e = getNode(hash(key), key)) == null)
            return null;
        if (accessOrder)
            afterNodeAccess(e);
        return e.value;
    }

    void afterNodeAccess(Node<K,V> e) { // move node to last
    }
```
当accessOrder为true时，就会执行afterNodeAccess方法，该方法就会把元素移到最后一个位置上。    
通过源码可以发现，LindHashMap是基于双向链表实现的   
```
    /**
     * The head (eldest) of the doubly linked list.
     */
    transient LinkedHashMap.Entry<K,V> head;

    /**
     * The tail (youngest) of the doubly linked list.
     */
    transient LinkedHashMap.Entry<K,V> tail;
```

可以看到基于LinkedHashMap实现LRU非常简单，只需要两个步骤   
1.继承LinkedHashMap，在构造方法指定accessOrder为true   
2.重写removeEldestEntry，当集合元素数量大于容量时，触发删除最早元素操作    

**手写实现**
但有时候面试官会要求我们自己实现，也就是手写lru了，太卷了~幸好我们早有准备    
可以参考LinkedHashMap双向链表的实现，这里我们也定义一个Node，用于保存元素   
```
	class Node {
		private K key;
		private V value;
		private Node pre;
		private Node next;

		public Node(K key, V value) {
			this.key = key;
			this.value = value;
		}
	}
```    
接着定义一个LRUCache，先把框框写出来，其中head,tail分别指向链表的首节点和尾节点，map是用于O(1)判断元素是否存在，否则需要遍历链表判断需要O(n)的时间复杂度。    
最重要的就是put/get方法的实现。   
```
public class LRUCache<K, V> {

	private int capacity;
	private Map<K, Node> map; //map用于O(1)判断元素是否存在
	private Node head;
	private Node tail;

	public LRUCache(int capacity) {
		this.capacity = capacity;
		map = new HashMap<>();
	}

	public void put(K key, V value) {
	}

	public V get(K key) {
	}
```

put方法的逻辑描述如下：   
```
	    /**
		 * 如果元素存在，更新值，将元素移到队尾
		 * 如果元素不存在，创建元素，添加到map。判断容量是否已满
		 * 是，移除队首元素，map移除队首元素，将元素添加到对尾
		 * 否，将元素添加到队尾
		 */
```
代码实现如下，完全是按照上面的逻辑，非常好理解    
```
	public void put(K key, V value) {
		Node node = map.get(key);
		if (node != null) {
			node.value = value;
			moveNodeToTail(node);
			return;
		}
		node = new Node(key, value);
		if (map.size() == capacity) {
			Node remove = removeHeadNode();
			map.remove(remove.key);
		}
		addNodeToTail(node);
		map.put(key, node);
	}
```
get方法就比较简单，如下   
```
	public V get(K key) {
		Node node = map.get(key);
		if (node == null) {
			return null;
		}
		//访问元素，将元素重新添加到队尾
		moveNodeToTail(node);
		return node.value;
	}
```
moveNodeToTail表示将节点移到队尾，removeHeadNode表示移除队首节点，addNodeToTail表示添加一个节点到队尾。   
代码如下：   
```
	private void moveNodeToTail(Node node) {
		if (node == tail) {
			//最后一个元素，不需要移动
			return;
		}

		node.next.pre = node.pre;
		if (node != head) {
			//队首节点没有pre
			node.pre.next = node.next;
		}

		tail.next = node;
		node.pre = tail;
		node.next = null;

		tail = node;
	}

	private Node removeHeadNode() {
		Node first = head;
		Node next = head.next;
		if (next == null) {
			//只有一个队首元素
			head = null;
			tail = null;
			return first;
		}
		next.pre = null;
		head = next;
		return first;
	}

	private void addNodeToTail(Node node) {
		if (head == null || tail == null) {
			//第一个元素
			head = node;
			tail = node;
			return;
		}
		tail.next = node;
		node.pre = tail;
		tail = node;
	}
```
初看这几个方法有点头晕，但实际非常简单。    
我们以比较复杂的moveNodeToTail为例，假设现在有3个Node，其结构如下：  
![image](2)   

当我们需要把中间这个节点移到队尾时，第一步就是把它摘掉，然后把它前后的节点的pre,next指针连接起来  
![image](3)   
对应代码   
```
	    node.next.pre = node.pre;
		if (node != head) {
			//队首节点没有pre
			node.pre.next = node.next;
		}
```

接着把它添加到队尾，跟最后一个节点tail连接起来   
![image](4)   
对应代码
```
		tail.next = node;
		node.pre = tail;
		node.next = null;
```

最后再把tail指向最后一个节点   
![image](5)    
对应代码   
```
	tail = node;
```
其它方法也都是思想，非常容易理解。我们还可以加一个方法来遍历链表   
```
	public void foreach(BiConsumer<K, V> consumer) {
		if (head == null) {
			return;
		}
		//遍历链表
		Node node = head;
		while (node != null) {
			consumer.accept(node.key, node.value);
			node = node.next;
		}
	}
```



