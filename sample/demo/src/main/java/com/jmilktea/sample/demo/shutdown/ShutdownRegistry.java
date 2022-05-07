package com.jmilktea.sample.demo.shutdown;

import com.google.common.collect.Lists;
import com.google.common.eventbus.EventBus;

import java.util.List;

/**
 * @author huangyb1
 * @date 2022/5/5
 */
public class ShutdownRegistry {

	public static List<EventBus> buses = Lists.newArrayList();

	public static void register(Shutdown shutdown) {
		EventBus eventBus = new EventBus("application-showdown");
		eventBus.register(shutdown);
		buses.add(eventBus);
	}

	public static void showdown() {
		buses.forEach(eventBus -> {
			eventBus.post(new ShutdownEvent(System.currentTimeMillis()));
		});
	}
}
