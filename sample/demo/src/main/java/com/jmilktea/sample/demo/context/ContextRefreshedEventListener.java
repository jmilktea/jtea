package com.jmilktea.sample.demo.context;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * @author huangyb1
 * @date 2021/8/2
 */
@Component
public class ContextRefreshedEventListener implements ApplicationListener<ContextRefreshedEvent> {

	@Override
	public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
		SimpleBean bean = contextRefreshedEvent.getApplicationContext().getBean(SimpleBean.class);
		System.out.println("====ContextRefreshedEven2 " + bean.getName());
	}

	@EventListener(ContextRefreshedEvent.class)
	public void onApplicationEventWithAnnotation(ContextRefreshedEvent contextRefreshedEvent) {
		SimpleBean bean = contextRefreshedEvent.getApplicationContext().getBean(SimpleBean.class);
		System.out.println("====ContextRefreshedEvent1 " + bean.getName());
	}
}
