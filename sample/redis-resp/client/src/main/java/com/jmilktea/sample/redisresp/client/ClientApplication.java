package com.jmilktea.sample.redisresp.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.core.RedisTemplate;
import redis.clients.jedis.Jedis;

@SpringBootApplication
public class ClientApplication implements ApplicationRunner {

	@Autowired
	private RedisTemplate redisTemplate;

	public static void main(String[] args) {
		SpringApplication.run(ClientApplication.class, args);
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		//redisTemplate.opsForValue().set("key", "hello");

		Jedis jedis = new Jedis("localhost", 16379);
		jedis.set("key", "hello");
	}
}
