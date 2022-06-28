package com.jmilktea.sample.demo.enhance;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author huangyb1
 * @date 2022/6/6
 */
@Slf4j
public class ResizableCapacityLinkedBlockingQueue<E> extends LinkedBlockingQueue<E> {

	@Getter
	private int capacity;

	public ResizableCapacityLinkedBlockingQueue(int capacity) {
		super(capacity);
		this.capacity = capacity;
	}

	public synchronized boolean setCapacity(int capacity) {
		boolean successFlag = true;
		try {
			Class superCls = this.getClass().getSuperclass();
			Field capacityField = superCls.getDeclaredField("capacity");
			Field countField = superCls.getDeclaredField("count");
			capacityField.setAccessible(true);
			countField.setAccessible(true);

			int oldCapacity = capacityField.getInt(this);
			capacityField.set(this, capacity);
			capacityField.setAccessible(false);

			AtomicInteger count = (AtomicInteger) countField.get(this);
			countField.setAccessible(false);

			if (capacity > count.get() && count.get() >= oldCapacity) {
				Method signalNotFull = superCls.getDeclaredMethod("signalNotFull");
				signalNotFull.setAccessible(true);
				signalNotFull.invoke(this);
				signalNotFull.setAccessible(false);
			}
			this.capacity = capacity;
		} catch (Exception ex) {
			log.error("dynamic modification of blocking queue size failed.", ex);
			successFlag = false;
		}

		return successFlag;
	}
}
