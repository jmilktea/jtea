package com.jmilktea.service.feign.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * @author huangyb
 * @date 2019/12/12
 */
@FeignClient(name = "provider2", url = "localhost:8081")
public interface FeignProvider2 {

    @RequestMapping(value = "/provide", method = RequestMethod.GET)
    String provide2(String id);
}
