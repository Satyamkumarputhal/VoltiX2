package com.voltix.telemetry.config;

import com.voltix.platform.config.VoltixProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
@EnableAsync
public class IngestionThreadPoolConfig {

    @Bean(name = "telemetryIngestionExecutor")
    public ThreadPoolTaskExecutor telemetryIngestionExecutor(VoltixProperties properties,
                                                            MeterRegistry meterRegistry) {
        AtomicLong rejectionCounter = meterRegistry.gauge("voltix.telemetry.rejections", new AtomicLong(0));
        VoltixProperties.Executor executorProperties = properties.getTelemetry().getExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(executorProperties.getCorePoolSize());
        executor.setMaxPoolSize(executorProperties.getMaxPoolSize());
        executor.setQueueCapacity(executorProperties.getQueueCapacity());
        executor.setThreadNamePrefix("voltix-worker-");
        executor.setTaskDecorator(mdcTaskDecorator());

        executor.setRejectedExecutionHandler((runnable, executorInstance) -> {
            rejectionCounter.incrementAndGet();
            throw new RejectedExecutionException("VoltiX Ingestion capacity saturated. Internal memory bounds hit.");
        });

        executor.initialize();
        meterRegistry.gauge("voltix.telemetry.executor.active", executor, ThreadPoolTaskExecutor::getActiveCount);
        meterRegistry.gauge("voltix.telemetry.executor.pool.size", executor, ThreadPoolTaskExecutor::getPoolSize);
        meterRegistry.gauge("voltix.telemetry.executor.queue.depth", executor,
                value -> value.getThreadPoolExecutor().getQueue().size());
        return executor;
    }

    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (context != null) {
                    MDC.setContextMap(context);
                } else {
                    MDC.clear();
                }
                try {
                    runnable.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        };
    }
}
