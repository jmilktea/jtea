package com.jmilktea.sample.demo.enhance;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author huangyb1
 * @date 2022/6/6
 */
public class EERejectedExecutionHandlerHolder {

	public interface EERejectedExecutionHandlerCounter extends RejectedExecutionHandler {

		LongAdder REJECT_COUNTER = new LongAdder();

		default void increment() {
			REJECT_COUNTER.add(1);
		}

		default long get() {
			return REJECT_COUNTER.sum();
		}
	}

	public static class EECallerRunsPolicy extends ThreadPoolExecutor.CallerRunsPolicy implements EERejectedExecutionHandlerCounter {
		@Override
		public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
			increment();
			super.rejectedExecution(r, e);
		}
	}

	public static class EEAbortPolicy extends ThreadPoolExecutor.AbortPolicy implements EERejectedExecutionHandlerCounter {
		@Override
		public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
			increment();
			super.rejectedExecution(r, e);
		}
	}

	public static class EEDiscardOldestPolicy extends ThreadPoolExecutor.DiscardOldestPolicy implements EERejectedExecutionHandlerCounter {
		@Override
		public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
			increment();
			super.rejectedExecution(r, e);
		}
	}

	public static class EEDiscardPolicy extends ThreadPoolExecutor.DiscardPolicy implements EERejectedExecutionHandlerCounter {
		@Override
		public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
			increment();
			super.rejectedExecution(r, e);
		}
	}
}
