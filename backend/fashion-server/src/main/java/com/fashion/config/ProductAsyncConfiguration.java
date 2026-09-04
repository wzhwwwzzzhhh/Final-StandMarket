package com.fashion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class ProductAsyncConfiguration {

    @Bean("productProjectionWakeupExecutor")
    public Executor productProjectionWakeupExecutor() {
        return executor("product-projection-", 2, 4, 100);
    }

    @Bean("productCacheRebuildExecutor")
    public Executor productCacheRebuildExecutor() {
        return executor("product-cache-rebuild-", 2, 4, 100);
    }

    private Executor executor(String prefix, int core, int max, int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
