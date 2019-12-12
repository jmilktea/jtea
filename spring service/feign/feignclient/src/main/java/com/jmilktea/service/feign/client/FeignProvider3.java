package com.jmilktea.service.feign.client;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author huangyb
 * @date 2019/12/12
 */
//@FeignClient(name = "provider3")
public interface FeignProvider3 {

    @RequestMapping(value = "/provide", method = RequestMethod.GET)
    String provide3(@RequestParam("id") String id);
}
