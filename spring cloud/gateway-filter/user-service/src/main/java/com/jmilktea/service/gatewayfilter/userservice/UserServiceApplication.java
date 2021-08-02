package com.jmilktea.service.gatewayfilter.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }

    @RequestMapping(value = "user/index", method = RequestMethod.GET)
    public String index() {
        return "user index";
    }

    @RequestMapping(value = "public/index", method = RequestMethod.GET)
    public String publicuri() {
        return "public";
    }
}
