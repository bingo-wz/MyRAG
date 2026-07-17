package com.wangzhi.knowledgebase.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ParsingConfig {

    @Bean(destroyMethod = "close")
    ExecutorService fileParsingExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("file-parser-", 0).factory());
    }
}
