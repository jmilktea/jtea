package com.jmilktea.sample.demo.enhance;

import lombok.Data;

/**
 * @author huangyb1
 * @date 2022/6/28
 */
@Data
public class ExecuteResult {

	private long successCount = 0L;
	private long failCount = 0L;
	private long filterCount = 0L;
	private long expCount = 0L;
	private long totalCount = 0L;
	private long totalExecuteTime = 0L;
	private float avgExecuteTime = 0L;
	private Exception firstExp;

	public long getTotalCount() {
		return successCount + failCount + filterCount + expCount;
	}

	public float getAvgExecuteTime() {
		if (getTotalCount() == 0) {
			return 0L;
		}
		return Float.valueOf(String.format("%.2f", (float) totalExecuteTime / getTotalCount()));
	}
}
