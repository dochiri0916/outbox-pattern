package com.dochiri.outboxpattern;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OutboxPatternApplication {

    static void main(String[] args) {
        SpringApplication.run(OutboxPatternApplication.class, args);
    }

}
