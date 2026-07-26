package com.dochiri.outboxpattern;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import com.dochiri.outboxpattern.application.post.service.CreatePostService;

@SpringBootApplication
@ComponentScan(
        basePackages = "com.dochiri.outboxpattern",
        excludeFilters = {
                @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = CreatePostService.class
                ),
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = ".*CreatePostTransactional"
                )
        }
)
@ConfigurationPropertiesScan
public class CdcConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CdcConsumerApplication.class, args);
    }
}
