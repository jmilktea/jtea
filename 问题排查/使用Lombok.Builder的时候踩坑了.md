## 背景
一个平常的日子，业务人员找到我，说用户的在黑名单库里的电话号码被展示出来了，没有被过滤掉。于是我找到相关代码，发现默认是过滤掉黑名单的。因为我们用一个Boolean对象来控制是否返回黑名单里的数据，默认值是false，也就是默认不返回。这里我用敏感信息来表示，伪代码如下:
```
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserQueryCondition {

	/**
	 * 是否返回用户头像
	 */
	private Boolean returnAvatar = false;

	/**
	 * 是否返回敏感信息
	 */
	private Boolean returnSensitiveInfo = false;

}
```
于是我不信邪，我在生产机器上使用curl，构造参数调用该接口，发现是过滤掉敏感信息的！
问题开始变得有趣起来，于是我上日志系统，查看接口日志的请求体&返回体，发现业务代码调用的接口确实把敏感信息返回了。
一时间，事情陷入僵局。

## 问题挖掘
于是我开始回归问题本身。业务代码中，对于该对象的使用是这样的：
```
		if (Boolean.FALSE.equals(returnSensitiveInfo)) {
			userList = filterSensitiveInfo(userList);
		}
```
如果if里面的过滤逻辑没执行到，那便会把敏感信息返回出去了。同时，我注意到项目中都是使用Builder来创建请求对象的，而我使用curl的方式去调用接口，是通过new 去创建对象的（因为UserQueryCondition类上贴了@NoArgsConstructor注解），莫非，使用Builder跟使用new创建出来的对象不一样？于是乎，我执行了以下测试：
```
	public static void main(String[] args) {
		UserQueryCondition queryCondition = UserQueryCondition.builder().build();
		if (!queryCondition.getReturnSensitiveInfo()) {
			System.out.println("filter some thing..");
		}
	}
```

我们来看运行结果:  
![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/builder1.png)    

发现竟然出现出现空指针了。debug看到该字段的值确实是null    

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/Builder2.png)    

但是，我们在上面的类中，不是已经给了默认值false吗？为何来到这里会变为null？  
难道用curl请求过去的接口，默认值就不会为null？于是我手动new了一个对象进行测试，结果如下：
![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/Builder3.png)   

可以看到，确实走进if里面的逻辑了。也就是说，这里的默认值false并没有丢失。
所以，问题出在Builder上。
打开 UserQueryCondition.Class文件，一切豁然开朗。（代码做了删减，便于阅读）  
```
public class UserQueryCondition {
    private Boolean returnAvatar = false;
    private Boolean returnSensitiveInfo = false;


    public static UserQueryCondition.UserQueryConditionBuilder builder() {
        return new UserQueryCondition.UserQueryConditionBuilder();
    }

    public Boolean getReturnAvatar() {
        return this.returnAvatar;
    }

    public Boolean getReturnSensitiveInfo() {
        return this.returnSensitiveInfo;
    }

    public void setReturnAvatar(final Boolean returnAvatar) {
        this.returnAvatar = returnAvatar;
    }

    public void setReturnSensitiveInfo(final Boolean returnSensitiveInfo) {
        this.returnSensitiveInfo = returnSensitiveInfo;
    }


    public UserQueryCondition() {
    }

    public UserQueryCondition(final Boolean returnAvatar, final Boolean returnSensitiveInfo) {
        this.returnAvatar = returnAvatar;
        this.returnSensitiveInfo = returnSensitiveInfo;
    }

    public static class UserQueryConditionBuilder {
        private Boolean returnAvatar;
        private Boolean returnSensitiveInfo;

        UserQueryConditionBuilder() {
        }

        public UserQueryCondition.UserQueryConditionBuilder returnAvatar(final Boolean returnAvatar) {
            this.returnAvatar = returnAvatar;
            return this;
        }

        public UserQueryCondition.UserQueryConditionBuilder returnSensitiveInfo(final Boolean returnSensitiveInfo) {
            this.returnSensitiveInfo = returnSensitiveInfo;
            return this;
        }

        public UserQueryCondition build() {
            return new UserQueryCondition(this.returnAvatar, this.returnSensitiveInfo);
        }

        public String toString() {
            return "UserQueryCondition.UserQueryConditionBuilder(returnAvatar=" + this.returnAvatar + ", returnSensitiveInfo=" + this.returnSensitiveInfo + ")";
        }
    }
}

```
从Class文件的代码中可以看到，当给某个类xxx贴上@Builder注解，会生成一个xxxBuilder的静态内部类。
当我们使用UserQueryCondition.builder().build()来构造对象时，实际是使用到该Builder的方法，具体如下：  
![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/Builder4.png)    
![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/Builder5.png)    

再看内部类的属性声明：    

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/Builder6.png)     

可以看到并没有默认值。所以一切可以解释了：
当我们调用.build()创建对象时，实际是调用到内部类的build()方法。然后new了一个目标类的对象，并将内部类的参数赋值给该目标类。最后返回出去。但此时我们在main方法中没有显示调用
.returnSensitiveInfo(false) 所以实际是 this.returnSensitiveInfo 是 null。进而返回出去的目标类中的rtContactBlacklist也是null了。这就跟我们前面debug时取出来的值呼应上了。
而当我们通过手动new对象时，是没有使用到Builder里面的逻辑的。所以默认值是生效的。  

## 解决方法
那么，该如何解决默认值丢失这件事情呢？我打开Builder的源码，发现如下：  
![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/Builder7.png)     

所以，可以通过@Builder.Default注解来解决。  
运行结果：  
![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/Builder8.png)    

可以看到默认值带上了。
回头再看一看新生成的字节码文件：  
![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/Builder9.png)  
!![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/Builder10.png)    

可以看到，当使用@Builder.Default注解时，Builder做了一些操作。解释起来就是：当我们使用Builder创建对象，但是不指定returnSensitiveInfo的值时，private boolean returnSensitiveInfo$set为false（boolean基础类型默认是false），而 if (!this.returnSensitiveInfo$set) 取反，从而将returnSensitiveInfo$value 赋值为UserQueryCondition.$default$returnSensitiveInfo()；而该方法返回false。

## 其他
不过要注意的是，使用了@Builder.Default注解后，用new方式来创建对象，在一些低版本的Lombok项目中，反而会丢失默认值。（有点离谱）。图示使用v1.16.16的Lombok  
![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/Builder11.png)    

这就奇怪了，这不是倒反天罡嘛。事实证明，Github上关于这个问题的讨论很是激烈：
https://github.com/projectlombok/lombok/issues/1347  

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/Builder12.png)  

从上面这个哥们的发言中，他说不仅是 list/set/map，任何其他 non-primitive 类型都会出现这个问题。
而下面这哥们的发言深得我心：  
![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/Builder13.png)    

他的意思就是说：为字段生成默认值的最直观方法就是从字段初始化中获取值，而不是需要额外的 Builder.Default 注解来标记。
那么到底是改了啥导致产生了这么一个奇怪的 BUG 呢？
注意下面 omega09 这个老哥的发言的后半句：field it will be initialized twice.
initialized twice，初始化两次，哪来的两次？
我们再看davidje13 这个哥们，他说是@NoArgsConstructor的原因。  

![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/Builder14.png)  

我们知道，@NoArgsConstructor就是让 lombok 给我们搞一个无参构造函数。
搞无参构造函数的时候，不是得针对有默认值的字段，进行一波默认值的初始化吗？
这个算一次了。
前面我们分析了 @Builder.Default 也要对有默认值的字段初始化一次。
所以是 twice，而且这两次干得都是同一个活。
开发者一看，这不行啊，得优化啊。
于是把 @NoArgsConstructor 的初始化延迟到了 @Builder.Default 里面去，让两次合并为一次了。
这样一看，用 Builder 模式的时候确实没问题了，但是用 new 的时候，默认值就没了。
不过，所幸的是，Lombok作者出面说明了该问题，并表示纳入考虑中。  
![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/Builder15.png)  
  
最终，该问题在v1.18.2 版本中得以解决。  
![image](https://github.com/jmilktea/jtea/blob/master/%E9%97%AE%E9%A2%98%E6%8E%92%E6%9F%A5/images/Builder16.png)  

## 总结
1、使用builder来构造对象，是通过内部类代理的方式来实现的。**内部类会擦除字段上已经书写的默认值。
除非我们给该字段打上@Builder.Default注解。**  
2、v1.18.2版本以前的Lombok，打上@Builder.Default注解后，使用new的方式去创建对象时，会丢失默认值，需注意。


