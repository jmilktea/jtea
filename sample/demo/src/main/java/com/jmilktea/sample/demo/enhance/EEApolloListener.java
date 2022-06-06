package com.jmilktea.sample.demo.enhance;

import com.ctrip.framework.apollo.model.ConfigChange;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.ctrip.framework.apollo.spring.annotation.ApolloConfigChangeListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author huangyb1
 * @date 2022/6/6
 */
@Slf4j
@Component
public class EEApolloListener {

	@ApolloConfigChangeListener
	public void listen(ConfigChangeEvent configChangeEvent) {
		if (EnhanceExecutorContainer.MAP.isEmpty()) {
			return;
		}
		for (Map.Entry<String, EnhanceExecutor> entry : EnhanceExecutorContainer.MAP.entrySet()) {
			//coreSize
			ConfigChange coreSizeChange = configChangeEvent.getChange(entry.getKey() + ".coreSize");
			if (coreSizeChange != null) {
				String oldValue = String.valueOf(entry.getValue().getCorePoolSize());
				log.info(entry.getKey() + " pool change coreSize from {} to {}", oldValue, coreSizeChange.getNewValue());
				executeChange(() -> entry.getValue().setCorePoolSize(Integer.valueOf(coreSizeChange.getNewValue())),
						entry.getKey(), "coreSize", oldValue, coreSizeChange.getNewValue());
			}
			//maximumSize
			ConfigChange maximumPoolSizeChange = configChangeEvent.getChange(entry.getKey() + ".maximumPoolSize");
			if (maximumPoolSizeChange != null) {
				String oldValue = String.valueOf(entry.getValue().getMaximumPoolSize());
				log.info(entry.getKey() + " pool change maximumPoolSize from {} to {}", oldValue, maximumPoolSizeChange.getNewValue());
				executeChange(() -> entry.getValue().setMaximumPoolSize(Integer.valueOf(maximumPoolSizeChange.getNewValue())),
						entry.getKey(), "maximumPoolSize", oldValue, maximumPoolSizeChange.getNewValue());
			}
			//keepAliveSecond
			ConfigChange keepAliveSecondChange = configChangeEvent.getChange(entry.getKey() + ".keepAliveSecond");
			if (keepAliveSecondChange != null) {
				String oldValue = String.valueOf(entry.getValue().getKeepAliveSecond());
				log.info(entry.getKey() + " pool change keepAliveSecond from {} to {}", oldValue, keepAliveSecondChange.getNewValue());
				executeChange(() -> entry.getValue().setKeepAliveSecond(Integer.valueOf(keepAliveSecondChange.getNewValue())),
						entry.getKey(), "keepAliveSecond", oldValue, keepAliveSecondChange.getNewValue());
			}
			//queueCapacity
			ConfigChange queueCapacityChange = configChangeEvent.getChange(entry.getKey() + ".queueCapacity");
			if (queueCapacityChange != null) {
				String oldValue = String.valueOf(entry.getValue().getQueueCapacity());
				log.info(entry.getKey() + " pool change queueCapacity from {} to {}", oldValue, queueCapacityChange.getNewValue());
				executeChange(() -> entry.getValue().setQueueCapacity(Integer.valueOf(queueCapacityChange.getNewValue())),
						entry.getKey(), "queueCapacity", oldValue, queueCapacityChange.getNewValue());
			}
		}
	}

	private void executeChange(Runnable runnable, String poolName, String fieldName, String oldValue, String newValue) {
		try {
			runnable.run();
		} catch (Exception ex) {
			log.error("change {} poll {} from {} to {} error", poolName, fieldName, oldValue, newValue, ex);
		}
	}
}
