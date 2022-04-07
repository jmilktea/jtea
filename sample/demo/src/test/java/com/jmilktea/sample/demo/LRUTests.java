package com.jmilktea.sample.demo;

import com.jmilktea.sample.demo.lru.LRUCache;
import com.jmilktea.sample.demo.lru.LRULinkedHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * @author huangyb1
 * @date 2022/3/30
 */
@SpringBootTest
public class LRUTests {

	@Test
	public void testLRULinkedHashMap() {
		LRULinkedHashMap<Integer, Integer> lru = new LRULinkedHashMap<>(10);
		lru.put(1, 1);
		lru.put(2, 2);
		lru.put(3, 3);
		lru.put(4, 4);
		lru.put(5, 5);
		lru.put(6, 6);
		lru.put(7, 7);
		lru.put(8, 8);
		lru.put(9, 9);
		lru.put(10, 10);

		lru.put(11, 11);
		lru.put(12, 12);
		lru.get(8);
		lru.put(10, 10);
		lru.keySet().forEach(k -> System.out.println(k));
	}

	@Test
	public void testLRUCache() {
		LRUCache<Integer, Integer> lruCache = new LRUCache<>(10);
		lruCache.put(1, 1);
		lruCache.put(2, 2);
		lruCache.put(3, 3);
		lruCache.put(4, 4);
		lruCache.put(5, 5);
		lruCache.put(6, 6);
		lruCache.put(7, 7);
		lruCache.put(8, 8);
		lruCache.put(9, 9);
		lruCache.put(10, 10);
		lruCache.put(10, 10);
		lruCache.put(10, 10);

		lruCache.put(11, 11);
		lruCache.put(12, 12);
		lruCache.get(8);
		lruCache.put(10, 10);
		lruCache.foreach((key, value) -> {
			System.out.println(key);
		});
	}
}
