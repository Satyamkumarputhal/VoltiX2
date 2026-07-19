package com.voltix.telemetry.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableAsync
public class IngestionThreadPoolConfig {

    /**
     * Real-time count of telemetry worker tasks currently executing on the
     * virtual-thread executor. Incremented when a decorated task begins running
     * and decremented when it completes (see {@link #mdcTaskDecorator(AtomicInteger)}).
     * Registered as the gauge {@code voltix.telemetry.executor.active} so the value
     * reflects genuine in-flight load rather than a hardcoded constant.
     *
     * <p>Note: {@code pool.size} and {@code queue.depth} gauges are intentionally NOT
     * registered. A {@link SimpleAsyncTaskExecutor} backed by virtual threads has no
     * bounded worker pool and no work queue (each task gets its own virtual thread
     * immediately), so those two values would be meaningless. Reporting a real
     * concurrency signal is preferred over publishing undefined/fake constants.
     */
    @Bean(name = "telemetryInFlightTasks")
    public AtomicInteger telemetryInFlightTasks(MeterRegistry meterRegistry) {
        return meterRegistry.gauge("voltix.telemetry.executor.active", new AtomicInteger(0));
    }

    @Bean(name = "telemetryIngestionExecutor")
    public TaskExecutor telemetryIngestionExecutor(TaskDecorator mdcTaskDecorator) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setVirtualThreads(true);
        executor.setThreadNamePrefix("voltix-worker-");
        executor.setTaskDecorator(mdcTaskDecorator);
        return executor;
    }

    @Bean
    public TaskDecorator mdcTaskDecorator(AtomicInteger telemetryInFlightTasks) {
        return runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (context != null) {
                    MDC.setContextMap(context);
                } else {
                    MDC.clear();
                }
                telemetryInFlightTasks.incrementAndGet();
                try {
                    runnable.run();
                } finally {
                    telemetryInFlightTasks.decrementAndGet();
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
