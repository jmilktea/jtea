## 背景
有这样一个需求，需要给人员分配任务，每个人有上限，分配时按照某种排序规则，尽可能公平的分配。  
例如有4个人员，A(10),B(12),C(15),D(18)，括号内表示当前人员手上的任务数。需求的分配逻辑是优先分配给任务数较少的人员，当任务数相等时，进行循环分配。
如下一个任务是分配给A(11)，其依然是最少，则下一次继续分配给A(12)，此时A和B相等，则后续几次的分配是：B,A,A,B,B,A，也就是任务相等时从头到尾，再从尾到头分配。  
用一张图表示更清晰，整个分配过程如下：  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E7%AE%97%E6%B3%95/images/rbt-task.png)  

整个逻辑实现起来还不太简单，有两个核心点
1. 优先分配拥有最低任务的人
2. 任务相等时，循环分配  

我们看最初的实现如下，这里实现的只是循环分配，还没有实现优先分配最低任务人员(当时还没做)，如果需要实现，需要改如何获取user的逻辑，还得判断任务数相时等等逻辑...
```
private void distribute() {
	int round = 1, index = 0; //轮次，单数正向分配，双数逆向分配
	List<User> users = Lists.newArrayList("A", "B", "C", "D"); //要分配的人员
	while (true) {
		if (CollectionUtils.isEmpty(users)) {
			//没有要分配的人员
			break;
		}
		List<Job> jobs = getJobs(); //排序获取任务
		if (CollectionUtils.isEmpty(jobs)) {
			//没任务了
			break;
		}
		for (Job job : jobs) {
			User user; //这个任务应该分配到谁
			if (round % 2 == 0) {
				user = users.get(users.size() - 1 - index % user.size());
			} else {
				user = users.get(index % users.size());
			}
			try {
				boolean result = service.generateJob(user, job); //生成任务
				if (result) {
					user.addJobCount(); //任务数+1
					if (user.getJobCount() >= user.getMaxTaskNum()) {
						users.remove(user); //任务达到上限，移除，不再分配
						if (CollectionUtils.isEmpty(users)) {
							break;
						}
					}
				} else {
					generateJobFail(job);
				}
			} catch (Exception e) {
				log.error("distribute job error:{}", e.getMessage(), e);
			}
			index++;
			if (index % users.size() == 0) {
				round++;
			}
		}
	}
}
```

可以看到整个过程实现起来比较复杂，需要判断各种条件，取余，计算等，后续维护起来成本比较大，也容易出错。

## 使用 TreeSet  
我们知道Set是一个集合接口，它里面的元素都是不重复的，而TreeSet是Set的一个实现类，并且它是有序的，也就是它是一个排好序且元素唯一的集合。  
那么TreeSet如何实现排序的呢？它是基于元素的Comparable接口，compare方法决定如何比较两个元素的大小。例如Integer,String等基本类型都实现了该接口，
当然自定义类也可以实现该接口实现比较规则。

我们看使用TreeSet如何实现这个需求呢
1. 优先分配拥有最低任务的人
2. 任务相等时，循环分配  

对于1，我们可以用任务数排好序，那么TreeSet.pollFirst获取到的就是任务最低的人，这是TreeSet的天然功能。  
对于2，如果任务数相等，如何实现循环？如果仅仅按照任务数排序，则无法实现。我们可以在任务数相等时，加多一个任务分配的时间戳，如先分配A,记录时间戳T1，分配B,记录时间戳T2，
当A,B任务相等，我们让时间戳比较大的排在前面，即T2>T1，B排序往前，那么下次分配时就是先分配B。这个是通过Comparable接口实现的。

我们看具体实现逻辑
```
private void distribute() {
	TreeSet<User> jobTreeSet = TreeSetUtils.init("A", "B", "C", "D"); //要分配的人员
	while (true) {
		if (jobTreeSet.isEmpty()) {
			break; //分配完成
		}			
		List<Job> jobs = getJobs(); //排序获取任务
		if (CollectionUtils.isEmpty(jobs)) {
			break; //分配完成
		}
		for (Job job : jobs) {
			if (jobTreeSet.isEmpty()) {
				break; //分配完成
			}				
			User user = jobTreeSet.pollFirst(); //取最小任务数的人
			try {					
				boolean result = service.generateJob(user, job); //生成任务
				if (result) {
					if (user.addJobCount()) {							
						jobTreeSet.add(user); //未满，添加回去重新排序
					}
				} else {
					generateJobFail(job);
				}
			} catch (Exception e) {
				log.error("distribute job error:{}", e.getMessage(), e);
			}
		}
	}
}
```

关键的User定义如下
```
@Data
public class User implements Comparable<User> {

	private Long uid;

	private String cname;

	private Integer jobCount = 0;

	private Integer maxTaskNum = 0;

	private Long lastDistributeTime = 0L;

	private final static Integer[] RANDOM_RESULT = new Integer[]{-1, 1};

	public Boolean addJobCount() {
		lastDistributeTime = System.currentTimeMillis(); //记录分配时间
		return ++jobCount < maxTaskNum;
	}

	@Override
	public int compareTo(User compare) {
		if (Objects.equals(this.uid, compare.uid)) {
			return 0;
		}
		if (this.jobCount > compare.jobCount) {
			return 1;
		}
		if (this.jobCount < compare.jobCount) {
			return -1;
		}
		if (this.lastDistributeTime < compare.lastDistributeTime) {
			//任务数相等，先分配的，下一次慢分配
			return 1;
		}
		if (this.lastDistributeTime > compare.lastDistributeTime) {
			//任务数相等，慢分配的，下一次先分配
			return -1;
		}
		//任务数相等，分配时间也相等，随机取一个
		return RANDOM_RESULT[RandomUtils.nextInt(0, 1)];
	}
}
```

我们自定义了比较规则，先比较任务数，如果任务数相等则比较分配时间。  
可以看到整个过程非常清爽，没有取余和计算操作，逻辑也比较清晰，方便维护。

结合实际业务场景，我们简单做下性能测试：
```
	@Test
	public void test1() {
		TreeSet<User> treeSet = new TreeSet();
		//10000个人员，现有任务数随机1-100000，总任务数1000000
		for (int i = 1; i < 10000; i++) {
			User user = new User();
			user.setUid(Long.valueOf(i));
			user.setJobCount(RandomUtils.nextInt(1, 100000));
			treeSet.add(user);
		}
		//时间
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		//gc
		Runtime.getRuntime().gc();
		long freeMemory = Runtime.getRuntime().freeMemory();
		System.out.println("before memory(byte):" + freeMemory);
		for (int i = 1; i < 1000_000; i++) {
			User user = treeSet.pollFirst();
			user.setJobCount(user.getJobCount() + 1);
			user.setLastDistributeTime(System.currentTimeMillis());
			treeSet.add(user);
		}
		stopWatch.stop();
		System.out.println("use memory(byte):" + (freeMemory - Runtime.getRuntime().freeMemory()));
		System.out.println("use seconds:" + stopWatch.getTotalTimeSeconds());
	}
```
输出如下：
```
before memory:314792216
use memory:314728
use seconds:0.399
```

## TreeSet原理   
我们先看下TreeSet的继承图  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E7%AE%97%E6%B3%95/images/rbt-treeset.png)  
可以看到TreeSet继承了SortSet，支持排序功能，另外还有NavigableSet接口，该接口定义了一些导航方法，如pollFirst获取第一个元素，lower小于指定元素，higher大于指定元素等。  
另外它还有一个变量m，而这个m默认就是TreeMap，也就是TreeSet是通过TreeMap实现的，我们知道Map表示key-value格式的键值对，那么TreeSet用什么表示value呢？  
通过add方法的源码可以发现设置的value是一个PRESENT，它是一个静态的Object对象
```
    public boolean add(E e) {
        return m.put(e, PRESENT)==null;
    }

    // Dummy value to associate with an Object in the backing Map
    private static final Object PRESENT = new Object();
```

那么TreeMap的底层又是如何实现的呢？看到TreeMap源码第一句就是
```
A Red-Black tree based {@link NavigableMap} implementation.
```
也就是TreeMap是基于红黑树算法实现的，红黑树是一种特殊的平衡二叉树。  
严格的平衡二叉树(AVL)定义是：
1. 本身首先是一棵二叉搜索树。
2. 每个结点的左右子树的高度之差的绝对值（平衡因子）最多为1。  

普通的排序二叉树(没有平衡功能)，在最坏的情况下可能退化为链表结构，此时查询的时间复杂度为O(n)，相当于需要遍历整个链表。AVL树就是为了解决这个问题，通过旋转树，保证
节点间高度始终维持平衡，提高检索效率。但与此同时，为了维持绝对平衡，所付出的代价就是需要不停旋转树。红黑树通过维持一种相对平衡的状态，减少树旋转次数，在频繁新增和删除节点的情形下，
由于旋转次数少，它的性能要比AVL树更好，由于不是绝对平衡，树的高度可能会比AVL大，所以其检索效率在最坏情况下没有AVL树好，但依然有一个不错的表现，属于一种折中选择的思想。  

红黑树的定义如下：
1. 节点是红色或黑色
2. 根节点是黑色
3. 所有叶子都是黑色（叶子是NIL节点）
4. 每个红色节点的两个子节点都是黑色（从每个叶子到根的所有路径上不能有两个连续的红色节点）
5. 从任一节点到其每个叶子的所有路径都包含相同数目的黑色节点

当新增或者删除节点时，就可能打破上面的规则，就需要进行**着色**和**旋转**来重新调整。一颗红黑树例子如下：  
![image](https://github.com/jmilktea/jmilktea/blob/master/%E7%AE%97%E6%B3%95/images/rbt-tree.png)

红黑树在实际过程中有许多应用
1. java 中的TreeMap，TreeSet 底层数据结构都是红黑树
2. java8后，当HashMap发生hash冲突时，也用红黑树存储数据(之前是用链表) 
3. linux虚拟内存管理  






