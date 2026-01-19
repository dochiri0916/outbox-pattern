package com.example.outboxpattern;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OutboxPatternApplication {

    public static void main(String[] args) {
        SpringApplication.run(OutboxPatternApplication.class, args);
    }

}
