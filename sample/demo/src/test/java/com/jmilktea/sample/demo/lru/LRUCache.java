package com.jmilktea.sample.demo.lru;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * @author huangyb1
 * @date 2022/3/30
 */
public class LRUCache<K, V> {

	private int capacity;
	private Map<K, Node> map; //map用于O(1)判断元素是否存在
	private Node head;
	private Node tail;

	public LRUCache(int capacity) {
		this.capacity = capacity;
		map = new HashMap<>();
	}

	public void put(K key, V value) {
		/**
		 * 如果元素存在，更新值，将元素移到队尾
		 * 如果元素不存在，创建元素，添加到map。判断容量是否已满
		 * 是，移除队首元素，map移除队首元素，将元素添加到对尾
		 * 否，将元素添加到队尾
		 */
		Node node = map.get(key);
		if (node != null) {
			node.value = value;
			moveNodeToTail(node);
			return;
		}
		node = new Node(key, value);
		if (map.size() == capacity) {
			Node remove = removeHeadNode();
			map.remove(remove.key);
		}
		addNodeToTail(node);
		map.put(key, node);
	}

	public V get(K key) {
		Node node = map.get(key);
		if (node == null) {
			return null;
		}
		//访问元素，将元素重新添加到队尾
		moveNodeToTail(node);
		return node.value;
	}

	public void foreach(BiConsumer<K, V> consumer) {
		if (head == null) {
			return;
		}
		//遍历链表
		Node node = head;
		while (node != null) {
			consumer.accept(node.key, node.value);
			node = node.next;
		}
	}

	private void moveNodeToTail(Node node) {
		if (node == tail) {
			//最后一个元素，不需要移动
			return;
		}

		node.next.pre = node.pre;
		if (node != head) {
			//队首节点没有pre
			node.pre.next = node.next;
		}

		tail.next = node;
		node.pre = tail;
		node.next = null;
		
		tail = node;
	}

	private Node removeHeadNode() {
		Node first = head;
		Node next = head.next;
		if (next == null) {
			//只有一个队首元素
			head = null;
			tail = null;
			return first;
		}
		next.pre = null;
		head = next;
		return first;
	}

	private void addNodeToTail(Node node) {
		if (head == null || tail == null) {
			//第一个元素
			head = node;
			tail = node;
			return;
		}
		tail.next = node;
		node.pre = tail;
		tail = node;
	}

	class Node {
		private K key;
		private V value;
		private Node pre;
		private Node next;

		public Node(K k, V v) {
			this.key = k;
			this.value = v;
		}
	}
}
