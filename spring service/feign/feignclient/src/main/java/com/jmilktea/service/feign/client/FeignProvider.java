package com.jmilktea.service.feign.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * @author huangyb
 * @date 2019/12/12
 */
@FeignClient(name = "feignprovider", url = "localhost:8081")
public interface FeignProvider {

    @RequestMapping(value = "/provide", method = RequestMethod.GET)
    String provide(String id);
}
