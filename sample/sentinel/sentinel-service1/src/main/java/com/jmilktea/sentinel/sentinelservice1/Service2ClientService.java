package com.jmilktea.sentinel.sentinelservice1;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author huangyb1
 * @date 2021/10/22
 */
@Service
public class Service2ClientService {

	@Autowired
	private Service2Client service2Client;

	//@SentinelResource("feign")
	public void test(Integer p) {
		service2Client.test(p);
	}
}
