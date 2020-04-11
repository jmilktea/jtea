package com.jmilktea.sample.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Hooks;

@SpringBootApplication
public class WebApplication {

    public static void main(String[] args) {
		int I = 10;
		if (I > 1)
			System.out.println(I);
        Hooks.onNextDropped(s->{
            System.out.println("============" + s);
        });
        SpringApplication.run(WebApplication.class, args);
        Hooks.onNextDropped(s->{
            System.out.println("============" + s);
        });
    }

}
