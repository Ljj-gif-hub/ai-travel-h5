package org.example.traveljava.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池配置
 * 用于并行获取景点图片，不阻塞主线程
 */
@Configuration
public class ThreadPoolConfig {

    /**
     * 图片获取线程池
     * 核心线程数: 4，最大线程数: 8，队列容量: 32
     */
    @Bean("imageFetchExecutor")
    public ExecutorService imageFetchExecutor() {
        return new ThreadPoolExecutor(
                4,
                8,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(32),
                r -> {
                    Thread thread = new Thread(r, "image-fetch-worker");
                    thread.setDaemon(true);
                    thread.setPriority(Thread.NORM_PRIORITY);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}