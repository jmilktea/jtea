## 前言
[spring session](https://github.com/spring-projects/spring-session)提供了http session的管理，并提供了多种存储方式可以选择，例如我们常用的redis，实现分布式session。基于springboot,我们可以通过@EnableRedisHttpSession(redisFlushMode = RedisFlushMode.IMMEDIATE)轻松开启基于redis的session会话。  
有一个实际场景是统计在线的用户数(已登录用户)，我们就需要通关统计当前session来判断，并且在登出和过期时减去。session并没有提供一个方法可以直接获取全局会话数量的方法，这需要我们自己实现。  

## 实现  
基于session会话事件，通过源码可以看到spring session提供了如下事件：  
![image]()  
分别对应session的创建、删除、销毁和过期。我们可以通过@EventListener注解捕获这些事件：
```
	@EventListener
	public void onSessionCreated(SessionCreatedEvent createdEvent) {
		System.out.println("session create");
	}
	@EventListener
	public void onSessionDeleted(SessionDeletedEvent deletedEvent) {
		System.out.println("session deleted");
	}
	@EventListener
	public void onSessionDestroyed(SessionDestroyedEvent destroyedEvent) {
		System.out.println("session destroyed");
	}
	@EventListener
	public void onSessionExpired(SessionExpiredEvent expiredEvent) {
		System.out.println("session expired");
	}
```
这些事件都是spring提供的，基于serlvet还有一个HttpSessionListener，可以监听session和创建和销毁。
```
@Component
public class SpringSessionListener implements HttpSessionListener {

	@Override
	public void sessionCreated(HttpSessionEvent se) {
		System.out.println("session created");
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent se) {
		System.out.println("session destroyed");
	}
}
```
destroyed方法是我们再调用session.invalidate时触发的。  
基于上面的方式我们可以拦截登入，登出事件实现在线人数的判断。  
但是如果这些任务还需要进行类型区分，比如某种类型的登录数的话，我们就需要再写session时做一个标记，如：  
```
session.setAttribute("user", new SessionAttr(userId userType));
```
然后在事件里取出来判断，如session create事件：  
```
@EventListener
public void onSessionCreated(SessionCreatedEvent createdEvent) {
    SessionAttr sa = (SessionAttr)createdEvent.getSession().getAttribute("user");
	System.out.println("session create");
}
```
遗憾的是，这里获取到的sa对象是空的~，也就是说虽然创建事件触发了，但是我们此时还拿不到刚刚创建的session对象。怎么解决呢？  
servlet还定义了一个HttpSessionAttributeListener接口，当我们在给session attribute创建的时候会触发，如：
```
@Component
public class SpringSessionAttributeListener implements HttpSessionAttributeListener {

	@Override
	public void attributeAdded(HttpSessionBindingEvent se) {
		System.out.println("attribute add");
	}

	@Override
	public void attributeRemoved(HttpSessionBindingEvent se) {
		System.out.println("attribute remove");
	}

	@Override
	public void attributeReplaced(HttpSessionBindingEvent se) {
		System.out.println("attribute replace");
	}
}
```
这样我们可以通过判断给session设置或移除了什么attribute来判断session的创建和消耗。再次遗憾的是，这个是servlet定义的规范，spring session并没有对它进行实现，参考：https://github.com/spring-projects/spring-session/issues/5    

**解决方案**   
然后上面的create事件拿不到完整的session对象，但是幸运的是可以拿到sessionId，有了sessionId，我们可以自己去redis拿已经缓存的对象。
```
createdEvent.getSession().getId()
```
在redis上缓存是这个样子：   
![image]()  
后面那串就是sessionId,完整的key我们可以通过源码找到：
```
RedisOperationsSessionRepository.DEFAULT_NAMESPACE  + "sessions:" + sessionId;
```
还有一种方式，我们通过spring session的setAttribute方法源码可以发现，它有一个HttpSessionBindingListener接口，里面有valueBound和valueUnbound方法，当我们的对象实现了这个接口时，在值改变的时候，这个事件就会触发。也就是我们上面的SessionAttr对象实现该接口即可。  
![image]()
