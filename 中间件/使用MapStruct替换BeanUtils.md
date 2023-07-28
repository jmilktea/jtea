# 前言
为了更好的进行开发和维护，我们都会对程序进行分层设计，例如常见的三层，四层，每层各司其职，相互配合。也随着分层，出现了VO，BO，PO，DTO，每层都会处理自己的数据对象，然后向上传递，这就避免不了经常要将一个对象的属性拷贝给另一个对象。     

例如我有一个User对象和一个UserVO对象，要将User对象的10个属性赋值个UserVo的同名属性：   
一种方式是手写，一个属性一个属性赋值，相信大家最开始学习时都是这么干的，这种方式就是太低效了。   
在idea中可以安装插件帮我们快速生成set属性代码，虽然还是逐个属性赋值，但比一个个敲，效率提高了很多。  

上面两种方式虽然最原始，做起来很麻烦，容易出错，但程序运行效率是最高的，现在仍有不少公司要求这么做，一是这样运行效率高，二是不需要引入其它的组件，避免出现其它问题。   
但对于我们来说，这种操作要是多了，开发效率和代码可维护性都会受到影响，这种赋值属性代码很长，看起来很不舒服，所有有了下面几种方式。   

# bean copier    
**apache的BeanUtils**，内部使用了反射，效率很低，在阿里java开发规范中命令禁止使用，这里就不过多讨论。    
![image](https://github.com/jmilktea/jtea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/mapstruct-1.png)    

**spring的BeanUtils**，对apache BeanUtils做了优化，运行效率较高，可以使用。     
```
BeanUtils.copyProperties(source, target);
BeanUtils.copyProperties(source, target, "id", "createTime"); //不拷贝指定的字段
```

**cglib的BeanCopier**，使用动态技术代替反射，在运行时生成一个子类，只有在第一次动态生成类时慢，后面基本就本接近原始的set，所以呀运行效率比上面两种要高很多。   
```
BeanCopier beanCopier = BeanCopier.create(SourceData.class, TargetData.class, false);
beanCopier.copy(source, target, null);		
```

我们使用的是spring BeanUtils，至少出现过两次问题：   
一次是拷贝一方的对象类型变了，从int变成long，source.id int 拷贝到 target.id long 结果是空，因为类型不匹配，BeanUtils不会拷贝。由于是使用反射，所以当时修改类型时，只修改了编译报错的地方，忘记这种方式，导致结果都是空，这也很难怪开发，这种方式太隐蔽了。同样如果属性重命名，也会得到一个空，并且只能在运行时发现。      
另一次拷贝的时候会把所有属性都拷过去，漏掉忽略主键id，结果在插入的时候报了唯一索引冲突。我们的场景比较特殊，id，createTime，updateTime这三个字段是表必须有的，通常也是不能被拷贝的，如果每个地方都手写忽略，代码比较麻烦也容易忘记。   

上面3种方式都非常简单，意味着功能非常有限，如果你有一些复杂场景的拷贝，它们就无法支持，例如深拷贝，拷贝一个List<SourceData>。    
另外一个最重要的点是，它们都是运行时的，这意味着你无法在编译时得到任何帮助，无法提前发现问题。    
从标题可以看出我们本篇要讲的是另一个copier：[mapstruct](https://github.com/mapstruct/mapstruct)，接下来就看下它是解决我们问题的。    

# [MapStruct](https://github.com/mapstruct/mapstruct)
mapstruct是一个基于java注解处理器，用于生成类型安全且高性能的映射器。总结一下它有以下优点：  
1.高性能。使用普通方法赋值，而非反射，mapstruct会在编译期间生成类，使用原生的set方法进行赋值，所以效率和手写set基本是一样的。   
2.类型安全。mapstruct是编译时的，所以一旦有类型、名称等不匹配问题，就可以提前编译报错。   
3.功能丰富。mapstruct的功能非常丰富，例如支持深拷贝，指定各种拷贝行为。   
4.使用简单。你所需要做的就是定义接口和拷贝的行为，mapstruct会在编译期生成实现类。    

**示例**   
和学习其它组件一样，我们先用起来，准备两个类，SourceData，TargetData属性完全一样，其中TestData是另一个类。     
```
public class SourceData {

	private String id;
	private String name;
	private TestData data;
    private Long createTime;

	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public TestData getData() {
		return data;
	}
	public void setData(TestData data) {
		this.data = data;
	}
    public Long getCreateTime() {
		return createTime;
	}
	public void setCreateTime(Long createTime) {
		this.createTime = createTime;
	}
}
```

导入包
```
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>${org.mapstruct.version}</version>
</dependency>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.8.1</version>
            <configuration>
                <source>1.8</source>
                <target>1.8</target>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.mapstruct</groupId>
                        <artifactId>mapstruct-processor</artifactId>
                        <version>${org.mapstruct.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

定义接口，这里的Mapper是mapstruct的，可不是mybatis的。   
```
@Mapper
public interface BeanMapper {

	BeanMapper INSTANCE = Mappers.getMapper(BeanMapper.class);

	TargetData map(SourceData source);
}
```

使用
```
SourceData source = new SourceData();
source.setId("123");
source.setName("abc");
source.setCreateTime(System.currentTimeMillis());
TestData testData = new TestData();
testData.setId("123");

TargetData target = BeanMapper.INSTANCE.map(source);
System.out.println(target.getId() + ":" + target.getName() + ":" + target.getCreateTime());
//true
System.out.println(source.getData() == target.getData());
```

可以看到使用非常简单，默认情况下mapstruct是浅拷贝，所以看到最后一个输出是true。编译后我们可以在target目录下找到帮我们生成的一个接口实现类BeanMapperImpl，如下：  
![image](https://github.com/jmilktea/jtea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/mapstruct-2.png)  

**深拷贝**   
可以看到它也是帮生成set代码，且默认是浅拷贝，所以上面最后一个输出是true。如果想变成深拷贝，在map方法上标记一下DeepClone即可：
```
@Mapping(target = "data", mappingControl = DeepClone.class)
TargetData map(SourceData source);
```
重新编译一下，看到生成的代码变成如下，这次是深拷贝了。   
![image](https://github.com/jmilktea/jtea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/mapstruct-3.png)   

**集合拷贝**  
支持，新增一个接口方法接口。   
```
List<TestData> map(List<TestData> source);   
```

**类型不一致**   
如果我将TargetData的createTime改成int类型，再编译一下，生成代码如下：  
![image](https://github.com/jmilktea/jtea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/mapstruct-4.png)   

可以看到它会默认帮我们转换，但这是个隐藏的问题，如果我希望它能在编译时就提示，那么可以在Mapper注解上指定一些类型转换的策略是报错，如下：
```
@Mapper(typeConversionPolicy = ReportingPolicy.ERROR)
```
重新编译会提示错误：
```
java: Can't map property "Long createTime". It has a possibly lossy conversion from Long to Integer.
```

**禁止隐式转换**   
如果我将类型改成string呢，编译又正常了，生成代码如下：  
![image](https://github.com/jmilktea/jtea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/mapstruct-5.png)
 
对于string和其它基础类型的包装类，它会隐式帮我们转换，这也是个隐藏问题，如果我希望它能在编译时就提示，可以定义一个注解，并在Mapper中指定它，如下：
```
@Retention(RetentionPolicy.CLASS)
@MappingControl(MappingControl.Use.DIRECT)
@MappingControl(MappingControl.Use.MAPPING_METHOD)
@MappingControl(MappingControl.Use.COMPLEX_MAPPING)
public @interface ConversationMapping {
}

@Mapper(typeConversionPolicy = ReportingPolicy.ERROR, mappingControl = ConversationMapping.class)
```
重新编译会提示报错：
```
java: Can't map property "Long createTime" to "String createTime". Consider to declare/implement a mapping method: "String map(Long value)".
```
这个可以参见issus上的讨论：[issus1428](https://github.com/mapstruct/mapstruct/issues/1428) [issus3186](https://github.com/mapstruct/mapstruct/issues/3186)

**忽略指定字段**    
忽略字段可以使用Mapping注解的ignore属性，如下：
```
@Mapping(target = "id", mappingControl = DeepClone.class)
```

如果我想忽略某些字段，并且复用起来，就像我们的场景应用，可以定义一个IgnoreFixedField注解，然后打在方法上
```
@Mapping(target = "id", ignore = true)
@Mapping(target = "createTime", ignore = true)
@Mapping(target = "updateTime", ignore = true)
@Target(METHOD)
@Retention(RUNTIME)
@Documented
@interface IgnoreFixedField {
}

@IgnoreFixedField
@Mapping(target = "data", mappingControl = DeepClone.class)
TargetData map(SourceData source);
```
这样只要打上这个注解，这3个字段就不会拷贝了。    

**与lombok集成**    
如果你的项目使用了lombok，上面的代码可能没法正常工作。需要在maven对lombok也做下配置，在上面的annotationProcessorPaths加入如下配置即可。   
```
<path>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.24</version>
</path>
```

上面只是结合本人的实际场景的一些例子，mapstruct还有更多的功能，参见官方文档。

# 总结
会用之后我们可以学习一下它的原理了，这也是我们平时学习一个新的东西的习惯，别一下子就扎到原理，源码里头，这样会严重打击学习热情，要先跑起来先，看到成果后你会更有激情学习下去。    
其实mapstruct的原理和lombok是一样的，都是在编译期间生成代码，而不会影响运行时。例如我们最常见的@Data注解，查看源文件你会发现getter/setter生成了，源文件的类不会有@Data注解。   

java代码编译和执行的整个过程包含三个主要机制：1.java源码编译机制 2.类加载机制 3.类执行机制。其中java源码编译由3个过程组成：1.分析和输入到符号表 2.注解处理 3.语义分析和生成class文件。如下：   
![image](https://github.com/jmilktea/jtea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/images/mapstruct-6.png)    

其中annotation processing就是注解处理，jdk7之前采用APT技术，之后的版本使用了JSR 269 API。    
JSR是什么？java Specification Requests，Java规范提案，是指向JCP(Java Community Process)提出新增一个标准化技术规范的正式请求。jsr 269是什么？[在这里](https://jcp.org/en/jsr/detail?id=269)    

注解我们非常熟悉，其实java里的注解有两种，一种是运行时注解，如常用@Resource，@Autowired，另一种是编译时注解，如lombok的@Data。  
编译时注解主要作用是在编译期间生成代码，这样就可以避免在运行时使用反射。编译时注解处理核心接口是Processor，它有一个抽象实现类AbstractProcessor封装了许多功能，如果要实现继承它即可。   
知道原理后，我们完全可以模仿lombok写一个简单的生成器，更多信息可以参考这篇文章：[Java编译期注解处理器AbstractProcessor](https://mp.weixin.qq.com/s?__biz=MzIzOTU0NTQ0MA==&mid=2247533063&idx=1&sn=0a78b877faf099c50211cd4aa7b191a2&chksm=e92a7f08de5df61e19a615ae1919f725cb8422fe68165e17a120b4250a6188ab2b42ae119ba9&mpshare=1&scene=1&srcid=0728iLEeeaAHa7tBBoSp8CPL&sharer_sharetime=1690507811053&sharer_shareid=20455070706123a29e0761599e77f885&version=4.1.0.6011&platform=win#rd)    

关于性能，知道原理后其实你也知道根本不用担心mapstruct的性能问题了，可以参考这个：[benchmark](https://github.com/arey/java-object-mapper-benchmark)       
如果要说它的缺点，就是得为了这个简单的拷贝功能导这个包，如果你的程序只有很少的拷贝，那手动写一下也未尝不可，如果有大量拷贝需求，那就推荐使用了。   


