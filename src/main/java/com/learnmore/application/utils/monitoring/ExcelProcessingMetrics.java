package com.learnmore.application.utils.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Micrometer metrics helper dedicated to Excel processing so that teams can observe the rollout of
 * the reactive implementation without instrumenting every caller separately. This component is
 * optional and does not impact existing logic, but it makes it easy to track adoption and
 * performance characteristics.
 */
@Component
public class ExcelProcessingMetrics {

    private final Counter traditionalCounter;
    private final Counter reactiveCounter;
    private final Timer traditionalTimer;
    private final Timer reactiveTimer;

    public ExcelProcessingMetrics(MeterRegistry meterRegistry) {
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");

        traditionalCounter = Counter.builder("excel.processing.traditional.count")
                .description("Number of Excel processing executions using the synchronous path")
                .register(meterRegistry);

        reactiveCounter = Counter.builder("excel.processing.reactive.count")
                .description("Number of Excel processing executions using the reactive path")
                .register(meterRegistry);

        traditionalTimer = Timer.builder("excel.processing.traditional.timer")
                .description("Execution time for synchronous Excel processing")
                .register(meterRegistry);

        reactiveTimer = Timer.builder("excel.processing.reactive.timer")
                .description("Execution time for reactive Excel processing")
                .register(meterRegistry);
    }

    public Runnable trackTraditional(Runnable runnable) {
        return () -> traditionalTimer.record(() -> {
            traditionalCounter.increment();
            runnable.run();
        });
    }

    public Runnable trackReactive(Runnable runnable) {
        return () -> reactiveTimer.record(() -> {
            reactiveCounter.increment();
            runnable.run();
        });
    }

    public void incrementTraditional() {
        traditionalCounter.increment();
    }

    public void incrementReactive() {
        reactiveCounter.increment();
    }
}
