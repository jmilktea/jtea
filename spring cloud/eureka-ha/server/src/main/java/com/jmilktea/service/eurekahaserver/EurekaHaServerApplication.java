package com.jmilktea.service.eurekahaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@EnableEurekaServer
@SpringBootApplication
public class EurekaHaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaHaServerApplication.class, args);
    }

}
