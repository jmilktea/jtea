package com.jmilktea.order.controller;

import com.jmilktea.order.feign.UserFeign;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author huangyb
 * @date 2019/11/16
 */
@RefreshScope
@RestController
@RequestMapping("config")
public class ConfigController {

    @Autowired
    private UserFeign userFeign;

    @Value("${config}")
    private String config;

    @RequestMapping(value = "get", method = RequestMethod.GET)
    public String get() {
        return "order-service config is:" + config + "，call user-service get config is:" + userFeign.config();
    }
}
