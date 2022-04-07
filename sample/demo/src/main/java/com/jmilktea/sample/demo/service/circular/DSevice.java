package com.jmilktea.sample.demo.service.circular;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author huangyb1
 * @date 2022/4/2
 */
@Service
public class DSevice {

	@Autowired
	private CSevice cSevice;

	public void transactional() {
		System.out.println(cSevice.toString());
	}
}
