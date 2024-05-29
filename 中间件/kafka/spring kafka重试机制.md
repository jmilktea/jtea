在使用消息队列进行异步、解耦和削峰时，消息消费失败是一个无法避免的问题，可能由于程序异常失败，可能由于调用第三方接口超时失败，或读写数据库超时失败，消费失败后如何处理，就是本篇我们要讨论的主题。     
消费失败如何处理，这个问题看起来很简单，重试就完了，但里面还是有一些细节需要注意，例如：   
如何重试，是连续重试还是退避重试？    
重试多久，是重试到一定时间还是一直重试？    
顺序消息如何处理？   

这里我们已kafka为例，来讨论这些问题，客户端使用spring boot + spring kafka(2.8+)。    
当我们在注册KafkaListenerContainerFactory时，不指定任何参数，使用的就是默认的重试机制。     
```
	@Bean
	@ConditionalOnMissingBean(name = "kafkaListenerContainerFactory")
	public KafkaListenerContainerFactory kafkaListenerContainerFactory(ConsumerFactory consumerFactory) {
		ConcurrentKafkaListenerContainerFactory factory = new ConcurrentKafkaListenerContainerFactory();
		factory.setConsumerFactory(consumerFactory);
		factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);		
		return factory;
	}
```
spring kafka针对错误处理定义了CommonErrorHandler接口，默认实现是DefaulErrorHadnler。   
默认是实现策略是在原队列上连续重试9次(加上正常的一次消费总共10次)，最后还是失败则打印日志，将消息发送到DLT队列。   
> DLT，Dead Letter Topic，死信队列，表示消息程序无法正常处理，通常需要人工介入。    
> 上面说到的原队列，意思是在原来的主题（更具体是原来的partition）上重试。   

可以看到默认的重试机制有几个问题，1、连续重试，实际发生在1s内，可能程序只是暂时不可用，瞬时重试很可能还是会失败。2、在原队列上重试，会阻塞后面消息的消费。    
熟悉rocketmq的同学都知道，rocketmq默认支持16次重试，且每次重试时间间隔会递增。如下：  

![image](https://github.com/jmilktea/jtea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/kafka/images/kafka-retry-1.png)   

使用spring kafka，也可以实现类似的机制。如下我们设置了一个从250ms开始，每次延迟递增1倍，最多重试16次的指数退避重试策略。   
```
	@Bean
	@ConditionalOnMissingBean(name = "kafkaListenerContainerFactory")
	public KafkaListenerContainerFactory kafkaListenerContainerFactory(ConsumerFactory consumerFactory) {
		ConcurrentKafkaListenerContainerFactory factory = new ConcurrentKafkaListenerContainerFactory();
		factory.setConsumerFactory(consumerFactory);
		factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
		//delay 250ms,1s,2s,4s,8s,16s,32s,64s,128s,256s,512s,1024s,2048s,4096s,2h,2h
		ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(16);
		backOff.setInitialInterval(250);
		backOff.setMultiplier(2);
		backOff.setMaxInterval(2 * 60 * 60 * 1000);
		factory.setCommonErrorHandler(new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate()), backOff));
		return factory;
	}
```

上面我们说到默认会在原队列上重试，这种写法虽然是指数退避重试，但会导致后面的消息都阻塞，无法消费。   
解决方案也不难，就是将失败的消息丢到一个新队列（另外的主题）上去，这样就不会阻塞后面的消息了。   
spring kafka提供了@RetryTopic注解，可以打在@KafkaListener注解的方法上，当发生消费异常时，消息就会被发送到另外的topic上，并使用重试线程拉取数据重新消费。    
代码如下：   
```
	@RetryableTopic(backoff = @Backoff(delay = 250), attempts = "16", fixedDelayTopicStrategy = FixedDelayStrategy.SINGLE_TOPIC)
	@KafkaListener(topics = "my_test")
	public void test(ConsumerRecord<String, String> consumerRecord) {
		if (consumerRecord.value().equals("2")) {
			System.out.println("================retry" + consumerRecord.value());
			throw new RuntimeException();
		}
		System.out.println("================" + consumerRecord.value() + ":" + Thread.currentThread().getName());
	}
```
连续发送1,2,3消息，可以看到2抛出异常，1,3正常消费，消息3没有被阻塞。    
消息2会重新投递到：my_test-retry-250，my_test-retry-500，my_test-retry-1000...等一系列重试topic上，命名规则是原topic加上"-retry"加上"-时间间隔"，每个时间间隔都会创建一个topic，由重试线程拉取数据重新消费。   
这显得非常差劲，这种情况下会创建非常多的topic，虽然@RetryTopic可以设置fixedDelayTopicStrategy = FixedDelayStrategy.SINGLE_TOPIC，但针对指数退避重试是无效的，这会使得topic数量爆发式增长。   
如果这种情况是不可接受的，只希望创建一个重试topic，只能使用固定重试时间，结合FixedDelayStrategy.SINGLE_TOPIC。   
```
@RetryableTopic(backoff = @Backoff(delay = 1000), attempts = "64", fixedDelayTopicStrategy = FixedDelayStrategy.SINGLE_TOPIC)
```
![image](https://github.com/jmilktea/jtea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/kafka/images/kafka-retry-2.png)     

> rocketmq的重试则显得要友好很多，它的重试由broker管理触发，默认只需要一个重试topic。  

当达到重试最大次数还失败了，就会发送到DLT，命名规则是原topic加上“-dlt”，例如：my_test-dlt。我们可以使用@DltHandler在同个class里，标记要处理死信消息方法，通常是将消息保存到数据库或es，人工介入处理。    
```
	@DltHandler
	public void dlt(ConsumerRecord<String, String> consumerRecord) {
		System.out.println("================" + consumerRecord.value());
	}
```

如果topic比较多，每个都配置重试容易遗漏，也比较麻烦。spring kafka支持在全局注入一个RetryTopicConfiguration，这样所有topic都会生效。如下：   
```
@Configuration
public class RetryConfiguration {

	@Bean
	public RetryTopicConfiguration myRetryTopic(KafkaTemplate template) {
		return RetryTopicConfigurationBuilder
				.newInstance()
				.fixedBackOff(1000)
				.maxAttempts(64)
				.useSingleTopicForFixedDelays()
				.create(template);
	}
}
```     

**多消费者组问题**    
一个topic可能被多个consumer group订阅消费，当其中一个消费失败时，理应由它进行重试，消费成功的组不用重试，否则就造成重复消费。   
例如：topic被A,B 两个consumer group消费，A消费失败，B消费成功，那只需要A consumer group 重新消费，B不能重新消费。    

按照上面代码示例，重试topic是原topic后缀加-retry，并没有区分consumer group，这样会导致所有consumer group都重新消费重试消息。   
解决方式是可以将当前consumer group id拼接在retry topic，这样就区分开了。   
```
@RetryableTopic(backoff = @Backoff(delay = 1000), attempts = "64", fixedDelayTopicStrategy = FixedDelayStrategy.SINGLE_TOPIC, retryTopicSuffix = "-my-consumer-group-id-retry")
    
//或

@Bean
public RetryTopicConfiguration myRetryTopic(KafkaTemplate template) {
	return RetryTopicConfigurationBuilder
		.newInstance()
		.fixedBackOff(1000)
		.maxAttempts(64)
		.useSingleTopicForFixedDelays()
		.retryTopicSuffix("-my-consumer-group-id-retry")
		.create(template);
}
```
> rocketmq的重试topic默认就会拼上consumerGroup。

**顺序消息问题**     
需要特别注意的是，如果是顺序消息，那就不能使用另外的重试队列了，否则顺序就无法保证。   

![image](https://github.com/jmilktea/jtea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/kafka/images/kafka-retry-3.png)    

顺序消息只能在原队列上不断重试，直到成功，且重试时间间隔不应过长，由于会阻塞后面的消息消费，需要做好监控告警。   
> rocketmq对顺序消息会做特殊处理，顺序消息的重试会在原队列，使用固定时间间隔，默认是3000ms。  

spring kafka这里可以将顺序消息排除，使用固定时间间隔重试策略。       
```
	@Bean
	public RetryTopicConfiguration myRetryTopic(KafkaTemplate template) {
		return RetryTopicConfigurationBuilder
				.newInstance()
				.fixedBackOff(1000)
				.maxAttempts(16)
				.useSingleTopicForFixedDelays()
				.retryTopicSuffix("-my-consumer-group-id")
				.excludeTopics(Lists.newArrayList("my-order-topic"))
				.create(template);
	}
```

**与rocketmq对比**    
上面也提到，rocketmq在使用方面更加友好，灵活，因为rocketmq在broker端就考虑处理了各种问题，重试，有序消息，事务消息，都是在broker端提供支持。而kafka broker并没有提供，spring kafka只能在客户端尽力实现。在重试机制方面，两者也大不相同，rocketmq是先将消息投递到schedule topic，在broker端使用线程扫描，如果达到时间，就重新投递到retry topic，对于开发者来说，客户端不需要关注太多细节。   
spring kafka还支持更多重试方式，例如先在原队列重试n次后，不成功再投递到重试队列重试，更多使用方法参考下方官网链接。    

![image](https://github.com/jmilktea/jtea/blob/master/%E4%B8%AD%E9%97%B4%E4%BB%B6/kafka/images/kafka-retry-4.png)      

 
参考文档：  
[spring kafka](https://docs.spring.io/spring-kafka/reference/retrytopic.html)   
[rocketmq](https://rocketmq.apache.org/zh/docs/featureBehavior/10consumerretrypolicy)   


