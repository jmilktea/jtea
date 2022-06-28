package com.jmilktea.sample.demo.enhance;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author huangyb1
 * @date 2022/6/6
 */

public interface EeRejectedExecutionHandler extends RejectedExecutionHandler {

	long getRejectCount();

	abstract class EeAbstractPolicy implements EeRejectedExecutionHandler {

		private LongAdder rejectCounter = new LongAdder();

		protected abstract void reject(Runnable r, ThreadPoolExecutor e);

		@Override
		public long getRejectCount() {
			return rejectCounter.sum();
		}

		@Override
		public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
			rejectCounter.add(1);
			reject(r, e);
		}
	}

	class EeCallerRunsPolicy extends EeAbstractPolicy {

		private ThreadPoolExecutor.CallerRunsPolicy rejectExecutor = new ThreadPoolExecutor.CallerRunsPolicy();

		@Override
		protected void reject(Runnable r, ThreadPoolExecutor e) {
			rejectExecutor.rejectedExecution(r, e);
		}
	}

	class EeAbortPolicy extends EeAbstractPolicy {

		private ThreadPoolExecutor.AbortPolicy rejectExecutor = new ThreadPoolExecutor.AbortPolicy();

		@Override
		protected void reject(Runnable r, ThreadPoolExecutor e) {
			rejectExecutor.rejectedExecution(r, e);
		}
	}

	class EeDiscardOldestPolicy extends EeAbstractPolicy {

		private ThreadPoolExecutor.DiscardOldestPolicy rejectExecutor = new ThreadPoolExecutor.DiscardOldestPolicy();

		@Override
		protected void reject(Runnable r, ThreadPoolExecutor e) {
			rejectExecutor.rejectedExecution(r, e);
		}
	}

	class EeDiscardPolicy extends EeAbstractPolicy {

		private ThreadPoolExecutor.DiscardPolicy rejectExecutor = new ThreadPoolExecutor.DiscardPolicy();

		@Override
		protected void reject(Runnable r, ThreadPoolExecutor e) {
			rejectExecutor.rejectedExecution(r, e);
		}
	}
}
