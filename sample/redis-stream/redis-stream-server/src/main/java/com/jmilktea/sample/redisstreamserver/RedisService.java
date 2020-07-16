package com.jmilktea.sample.redisstreamserver;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * @author huangyb1
 * @date 2020/6/28
 */
@Service
public class RedisService {

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	public void sendMessageToStream(String key, String value) {
		StringRecord record = StreamRecords.string(Collections.singletonMap(key, value)).withStreamKey("stream");
		RecordId recordId = this.stringRedisTemplate.opsForStream().add(record);
		System.out.println(String.format("message id:%s message timestamp:%s message sequence:%s", recordId.getValue(), recordId.getTimestamp(), recordId.getSequence()));
	}
}
