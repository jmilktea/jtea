package com.jmilktea.sample.demo.enhance;

import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.ctrip.framework.apollo.spring.annotation.ApolloConfigChangeListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * @author huangyb1
 * @date 2022/6/6
 */
@Slf4j
@Component
public class EeApolloListener implements ApplicationContextAware {

	private ApplicationContext applicationContext;

	private static final String eeStartPrefix = "ee.";
	private static final String corePoolSize = "corePoolSize";
	private static final String maximumPoolSize = "maximumPoolSize";
	private static final String keepAliveSecond = "keepAliveSecond";
	private static final String queueCapacity = "queueCapacity";

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@ApolloConfigChangeListener
	public synchronized void listen(ConfigChangeEvent configChangeEvent) {
		for (String changeKey : configChangeEvent.changedKeys()) {
			try {
				if (!changeKey.startsWith(eeStartPrefix)) {
					continue;
				}
				String[] splitNames = changeKey.split("\\.");
				if (splitNames.length != 3) {
					//ee.poolName.fieldName=value
					continue;
				}
				String poolName = splitNames[1];
				EnhanceExecutor pool = applicationContext.getBean(poolName, EnhanceExecutor.class);
				String poolField = splitNames[2];
				String newValue = configChangeEvent.getChange(changeKey).getNewValue();
				if (corePoolSize.equals(poolField)) {
					//corePoolSize
					executeChange(() -> pool.setCorePoolSize(Integer.valueOf(newValue)),
							poolName, corePoolSize, String.valueOf(pool.getCorePoolSize()), newValue);
				} else if (maximumPoolSize.equals(poolField)) {
					//maximumPoolSize
					executeChange(() -> pool.setMaximumPoolSize(Integer.valueOf(newValue)),
							poolName, maximumPoolSize, String.valueOf(pool.getMaximumPoolSize()), newValue);
				} else if (keepAliveSecond.equals(poolField)) {
					//keepAliveSecond
					executeChange(() -> pool.setKeepAliveSecond(Long.valueOf(newValue)),
							poolName, keepAliveSecond, String.valueOf(pool.getKeepAliveSecond()), newValue);
				} else if (queueCapacity.equals(poolField)) {
					//queueCapacity
					executeChange(() -> pool.setQueueCapacity(Integer.valueOf(newValue)),
							poolName, queueCapacity, String.valueOf(pool.getQueueCapacity()), newValue);
				} else {
					log.warn("pool change {} fail,not support field");
				}
			} catch (BeansException ex) {
				log.warn("pool change {} fail,the bean of EnhanceExecutor could not be found", changeKey, ex);
			} catch (Exception ex) {
				log.error("pool change {} error", changeKey, ex);
			}
		}
	}

	private void executeChange(Runnable runnable, String poolName, String fieldName, String oldValue, String newValue) {
		log.info("{} change {} from {} to {}", poolName, fieldName, oldValue, newValue);
		try {
			runnable.run();
		} catch (Exception ex) {
			log.error("{} change {} from {} to {} error", poolName, fieldName, oldValue, newValue, ex);
		}
	}
}
