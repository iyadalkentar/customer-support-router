package com.ikdev.customersupportrouter.chatservice.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Executor for the conversation-memory write path.
 *
 * <p>The AFTER_COMMIT Redis write runs async so a slow or stuck Redis never holds the
 * HTTP request thread. Deliberately SINGLE-THREADED: appends must stay in submission
 * order so the Redis list remains chronological (oldest first) — parallel appends to
 * the same conversation would interleave and reorder the context the classifier reads.
 * One thread is ample: each append is a single pipelined Redis round trip.
 *
 * <p>{@code CallerRunsPolicy} means a saturated queue degrades to running the write on
 * the caller (request) thread instead of rejecting the task — a rejection would surface
 * a 500 after commit, which is worse than the synchronous behavior this replaces.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "conversationMemoryExecutor")
    public Executor conversationMemoryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("conv-memory-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
