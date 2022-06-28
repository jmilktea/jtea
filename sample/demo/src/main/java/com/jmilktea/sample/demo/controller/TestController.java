package com.jmilktea.sample.demo.controller;

import com.jmilktea.sample.demo.bytebuddy.TimeClass;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author huangyb1
 * @date 2020/11/20
 */
@RestController
public class TestController {

	@RequestMapping(value = "/bytebuddy")
	public void testByteBuddy() throws InterruptedException {
		TimeClass timeClass = new TimeClass();
		timeClass.test();
		timeClass.test2();
	}
}
