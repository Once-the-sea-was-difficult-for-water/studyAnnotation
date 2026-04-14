package com.multiagent.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.multiagent")
public class MultiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultiAgentApplication.class, args);
    }
}
