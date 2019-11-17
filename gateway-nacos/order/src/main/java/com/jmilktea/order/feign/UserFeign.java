package com.jmilktea.order.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author huangyb
 * @date 2019/11/16
 */
@FeignClient(name = "user-service", path = "/user")
public interface UserFeign {

    @RequestMapping("config/get")
    String config();
}
