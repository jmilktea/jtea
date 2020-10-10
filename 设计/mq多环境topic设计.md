## 前言  
实际开发中我们的服务通常会部署多个环境，生产环境，预生产环境，测试环境，开发环境，还有开发人员的本地开发环境，对于mq消息来说，如果没有每个环境单独部署一套mq server，而是共同使用一个mq server，比如开发环境和开发人员本地开发时使用同一个mq server，那么就可能会发生“消息乱窜”的现象，即消息被其它环境的服务给消费了。这种现象在本地开发联调时比较常发生，大家使用同一个topic，消息就无法保证被谁消费。如图：  
![image]()    

## 解决方案  
这里假设开发环境有一套mq server，定义了一套topic，本地开发人员也是使用这个mq，为了开发环境和本地开发的消息不冲突，我们希望本地开发在topic后面加个_local后缀作为区分，如开发环境的topic是product_create，那么本地开发环境就是product_create_local，这样就不会相互影响了。我们可以定义一个mq.suffix配置，默认为空，也就是没有后缀，对于本地开发环境，需要利用spring boot的多环境配置文件，开发在本地加一个application-local.yml，并且配置mq.suffix:_local，在本地开发时启动都是使用local配置文件。这里以kafka为例，其它mq也是类似道理。
1. 手动拼接topic
要在每个topic后面加个后缀，也就是需要在发送和接收端都做这个处理。
发送端很好处理，就是在每次发送时手动拼接一个后缀，如:
```
kafkaTemplate.send(MqConfig.PRODUCT_CREATE + mqConfig.getSuffix(), data);
```
对于接收端，我们一般使用注解的方式，@KafkaListener允许我们使用SpEL表达式注入变量，kafka在会解析它，获取环境变量。如：
```
@KafkaListener(topics = MqConfig.PRODUCT_CREATE + "${mq.suffix}")
```
这种方式的缺点挺明显的，每次发送和接收都需要手动去拼接这个后缀，代码看起来比较乱，而且需要约束开发遵守这个规范。我们希望是在发送和接收不需要关注这个事情，对于发送和接收是透明的。

2. 自动生成topic  
我们先定义好正常的topic，要自动生成有后缀的topic，只需要在给topic赋值时，自动拼接上后缀即可。这里利用到spring bean生命周期的环节，在属性赋值时，可以动态修改属性值。我们定义一个MqConfig实现InitializingBean接口，在afterPropertiesSet里对属性进行修改。
```
@Data
@Slf4j
@ConfigurationProperties(prefix = "mq")
public class MqConfig implements InitializingBean {

	public static String PRODUCT_CREATE = "topic_product_create";
    public static String PRODUCT_DELETE = "topic_product_delete";

	private String suffix = "";

	@Override
	public void afterPropertiesSet() throws IllegalAccessException {
		Field[] fields = this.getClass().getDeclaredFields();
		for (Field field : fields) {
			if (Modifier.isStatic(field.getModifiers())) {
				String fieldValue = field.get(this).toString();
				if (fieldValue != null && fieldValue.startsWith("topic")) {
					field.setAccessible(true);
					String suffixTopic = fieldValue + suffix;
					System.setProperty(fieldValue, suffixTopic);
					field.set(this, suffixTopic);
					log.info("mq config:key:{},topic:{}", fieldValue, suffixTopic);
				}
			}
		}
	}
}
```
suffix还是我们根据环境做的配置，这里会自动拼接并修改到对应topic，发送时就不需要拼接了。这里还做了一个步骤System.setProperty(fieldValue, suffixTopic);通过这个设置就把拼接好的设置好环境变量中，在使用@KafkaListener(topics = "${product_create}")时，我们不再需要去拼接后缀了，当然这个${product_create}是我们动态设置的，也不需要在配置文件去配置，这样对于发送和接收还是像以前一样处理，如果有配置suffix，就会自动生成有后缀的topic，消息可以路由到正确的服务消费。
