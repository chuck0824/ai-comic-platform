package com.aicp.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置。
 * 避免 Spring 默认的 SimpleAsyncTaskExecutor 在生产环境无限创建线程。
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /** AI 生成任务专用线程池 — 有界队列 + CallerRunsPolicy 防止 OOM */
    @Bean("genTaskExecutor")
    public Executor genTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("gen-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // 注册 JVM 指标
        executor.initialize();
        log.info("Async genTaskExecutor 已初始化: core=2, max=8, queue=100");
        return executor;
    }
}
