package com.wangzhi.knowledgebase.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import org.springframework.scheduling.TaskScheduler;

@Configuration
@EnableScheduling
public class AsyncConfig {

    @Bean
    public TaskScheduler taskScheduler(
            @Value("${app.scheduling.await-termination-seconds:0}") int awaitTerminationSeconds) {
        ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();
        executor.setPoolSize(2);
        executor.setThreadNamePrefix("knowledge-import-");
        // 生产环境短暂等待在途任务；开发和测试立即中断并依靠租约恢复。
        int timeout = Math.max(0, awaitTerminationSeconds);
        executor.setWaitForTasksToCompleteOnShutdown(timeout > 0);
        executor.setAwaitTerminationSeconds(timeout);
        executor.setRemoveOnCancelPolicy(true);
        executor.initialize();
        return executor;
    }
}
