package com.jmilktea.sample.demo.shutdown;

import com.google.common.eventbus.Subscribe;

import java.util.function.Consumer;

/**
 * @author huangyb1
 * @date 2022/5/5
 */
public class Shutdown {

	protected boolean shutdown;
	private Consumer consumer;

	public Shutdown() {
		this(null);
	}

	public Shutdown(Consumer consumer) {
		this.consumer = consumer;
		ShutdownRegistry.register(this);
	}

	@Subscribe
	public void subcribe(ShutdownEvent se) {
		System.out.println(se.getTime());
		shutdown = true;
		if (consumer != null) {
			consumer.accept(se);
		}
	}
}
