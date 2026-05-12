package com.project.flowfinserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FlowfinServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowfinServerApplication.class, args);
    }

}
