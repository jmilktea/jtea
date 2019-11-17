package com.jmilktea.user.controller;

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

    @Value("${config}")
    private String config;

    @RequestMapping(value = "get", method = RequestMethod.GET)
    public String get() {
        return config;
    }
}
