package com.jmilktea.service.feign.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author huangyb
 * @date 2019/12/12
 */
@FeignClient(name = "feign-provider")
public interface FeignProvider3 {

	@RequestMapping(value = "/testTimeOut", method = RequestMethod.GET)
	String testTimeOut();
}
