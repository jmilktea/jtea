package com.jmilktea.sample.redisstreamserver;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * @author huangyb1
 * @date 2020/6/28
 */
@Component
public class StreamMsgRunner implements ApplicationRunner, DisposableBean {

	@Autowired
	RedisConnectionFactory redisConnectionFactory;

	@Autowired
	ThreadPoolTaskExecutor threadPoolTaskExecutor;

	@Autowired
	StreamMsgListener streamMsgListener;

	@Autowired
	StringRedisTemplate stringRedisTemplate;

	private StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer;

	@Override
	public void run(ApplicationArguments args) throws Exception {
		System.out.println("StreamMsgRunner init...");

		// 创建配置对象
		StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>>
				streamMessageListenerContainerOptions = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
				.builder()
				// 一次性最多拉取多少条消息
				.batchSize(5)
				// 消费消息的线程池
				.executor(this.threadPoolTaskExecutor)
				// 消息消费异常的handler
				.errorHandler(t -> {
					System.out.println("consume msg error:" + t.getMessage());
				})
				// 超时时间，设置为0，表示不超时（超时后会抛出异常）
				.pollTimeout(Duration.ZERO)
				// 序列化器
				.serializer(new StringRedisSerializer())
				.build();

		// 根据配置对象创建监听容器对象
		StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer = StreamMessageListenerContainer
				.create(this.redisConnectionFactory, streamMessageListenerContainerOptions);

		// 使用监听容器对象开始监听消费（使用的是手动确认方式）
		streamMessageListenerContainer.receive(Consumer.from("group-1", "consumer-1"),
				StreamOffset.create("stream", ReadOffset.lastConsumed()), this.streamMsgListener);

		this.streamMessageListenerContainer = streamMessageListenerContainer;
		// 启动监听
		this.streamMessageListenerContainer.start();

	}

	@Override
	public void destroy() throws Exception {
		this.streamMessageListenerContainer.stop();
	}
}
