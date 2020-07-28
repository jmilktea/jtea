![image](https://github.com/jmilktea/jmilktea/blob/master/%E5%9F%BA%E7%A1%80/images/spring%20bean%20life%20cycle.png)  

- 三个流程   
根据上图可以分为3个阶段，准备阶段->实例化bean阶段->bean销毁阶段。准备阶段是spring容器启动开始各种初始化，从配置文件，java config等获取bean的相关信息，也就是BeanDefination。严格来说，准备阶段不属于bean生命周期，因为此时只是收集bean的元信息，还没开始创建bean对象。实例化阶段就是bean对象创建阶段，生成可以使用的bean。销毁阶段就是bean销毁过程。

- BeanFactoryPostProcessor与BeanPostProcessor   
两种名字相似，BeanFactoryPostProcessor是BeanFactory后置处理器，此时Bean还未生成，但可以对BeanDefination进行修改，也就是修改Bean的元信息，以此改变Bean的定义。BeanPostProcessor是Bean后置处理器，此时Bean已经生成，可以对已生成的对象进行修改。  

- InstantiationAwareBeanPostProcessor的作用     
Instantiation可以理解为实例化，此时对象还未完整生成。Initialization可以理解为初始化，此时对象已经生成。InstantiationAwareBeanPostProcessor继承了BeanPostProcessor，可以bean实例化之前和属性注入之前做一些操作。而BeanPostProcessor做的都是在bean已经实例化并且属性已经初始化完成。

- 有了init-method为什么还需要@PostConstruct  
@PostConstruct并不是spring包下的，而是javax包下的，spring为了兼容，对它进行支持。而init-method是spring bean里的。
