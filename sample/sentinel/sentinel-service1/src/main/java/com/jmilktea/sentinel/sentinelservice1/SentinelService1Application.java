package com.jmilktea.sentinel.sentinelservice1;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.ArrayList;
import java.util.List;

@EnableFeignClients
@SpringBootApplication
public class SentinelService1Application {

	public static void main(String[] args) throws InterruptedException {
		SpringApplication.run(SentinelService1Application.class, args);
		//helloword();
	}

	private static void helloword() throws InterruptedException {
		int i = 0;
		initFlowRules();
		while (i < 100) {
			i++;
			if (i == 50) {
				Thread.sleep(1000);
			}
			Entry entry = null;
			try {
				entry = SphU.entry("HelloWorld");
				/*您的业务逻辑 - 开始*/
				System.out.println("hello world");
				/*您的业务逻辑 - 结束*/
			} catch (BlockException e1) {
				/*流控逻辑处理 - 开始*/
				System.out.println("block!");
				/*流控逻辑处理 - 结束*/
			} finally {
				if (entry != null) {
					entry.exit();
				}
			}
		}
	}

	private static void initFlowRules() {
		List<FlowRule> rules = new ArrayList<>();
		FlowRule rule = new FlowRule();
		rule.setResource("HelloWorld");
		rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
		// Set limit QPS to 20.
		rule.setCount(20);
		rules.add(rule);
		FlowRuleManager.loadRules(rules);
	}

}
