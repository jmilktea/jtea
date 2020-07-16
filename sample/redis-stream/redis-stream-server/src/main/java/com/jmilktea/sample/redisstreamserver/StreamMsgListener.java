package com.jmilktea.sample.redisstreamserver;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

/**
 * @author huangyb1
 * @date 2020/6/28
 */
@Component
public class StreamMsgListener implements StreamListener<String, MapRecord<String, String, String>> {

	@Autowired
	StringRedisTemplate stringRedisTemplate;

	@Override
	public void onMessage(MapRecord<String, String, String> message) {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {

		}
		RecordId messageId = message.getId();
		System.out.println(String.format("consume stream message:message id:%s value:%s", messageId, message.getValue()));

		//ack
		this.stringRedisTemplate.opsForStream().acknowledge("stream", message);
	}
}
