package com.jmilktea.sample.demo.lru;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author huangyb1
 * @date 2022/3/30
 */
public class LRULinkedHashMap<K, V> extends LinkedHashMap<K, V> {

	private int capacity;

	public LRULinkedHashMap(int capacity) {
		super(capacity, 0.75f, true);
		this.capacity = capacity;
	}

	@Override
	protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
		boolean b = size() > capacity;
		System.out.println("removeEldestEntry:" + b);
		return b;
	}
}
