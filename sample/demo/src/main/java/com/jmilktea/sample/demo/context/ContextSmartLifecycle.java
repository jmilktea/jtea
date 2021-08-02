package com.jmilktea.sample.demo.context;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * @author huangyb1
 * @date 2021/8/2
 */
@Component
public class ContextSmartLifecycle implements SmartLifecycle {

	@Autowired
	private SimpleBean simpleBean;

	private Boolean running;

	@Override
	public void start() {
		running = true;
		System.out.println("====SmartLifecycle start " + simpleBean.getName());
	}

	@Override
	public void stop() {
		System.out.println("====SmartLifecycle stop " + simpleBean.getName());
	}

	@Override
	public boolean isRunning() {
		return running == null ? false : running;
	}

	@Override
	public int getPhase() {
		return 0;
	}
}
