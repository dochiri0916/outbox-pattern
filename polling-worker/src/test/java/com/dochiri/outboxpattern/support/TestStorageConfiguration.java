package com.dochiri.outboxpattern.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class TestStorageConfiguration {

    @Bean
    @Primary
    public InMemoryFileStoragePort inMemoryFileStoragePort() {
        return new InMemoryFileStoragePort();
    }
}
