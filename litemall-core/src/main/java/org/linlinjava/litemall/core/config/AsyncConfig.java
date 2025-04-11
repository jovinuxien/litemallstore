package org.linlinjava.litemall.core.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.Beta;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableAsync
@EnableCaching
public class AsyncConfig {


    @Bean
    public Executor asyncIndexingExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("AsyncIndexing-");
        executor.initialize();
        return executor;
    }



    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .initialCapacity(100)
                        .maximumSize(500)
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .recordStats());


        // Here we can specify different settings for product caches
        cacheManager.registerCustomCache("products", Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build());
        // Here we can specify different settings for productDetails caches
        cacheManager.registerCustomCache("productDetails", Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterAccess(2, TimeUnit.HOURS)  // expire after last access
                .build());

        return cacheManager;
    }
}
