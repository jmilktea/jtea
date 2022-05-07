package com.jmilktea.sample.demo.shutdown;

import java.util.function.Consumer;

/**
 * @author huangyb1
 * @date 2022/5/5
 */
public class LoopShutdown extends Shutdown {

	public void loop(Consumer consumer) {
		while (true) {
			if (shutdown) {
				System.out.println("shutdown loop");
				break;
			}
			consumer.accept(null);
		}
	}

	public void loop(int forTotal, Consumer consumer) {
		for (int i = 0; i < forTotal; i++) {
			if (shutdown) {
				System.out.println("shutdown loop");
				break;
			}
			consumer.accept(null);
		}
	}
}
