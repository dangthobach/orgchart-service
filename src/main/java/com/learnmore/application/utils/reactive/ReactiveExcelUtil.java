package com.learnmore.application.utils.reactive;

import com.learnmore.application.excel.ExcelFacade;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Reactive facade for Excel processing that enables non-blocking, backpressure-aware processing
 * using the new ExcelFacade architecture. Each method wraps the true streaming processor and 
 * exposes a Reactor {@link Flux} or {@link Mono} for easy integration with WebFlux/R2DBC stacks.
 */
@Component
public class ReactiveExcelUtil {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveExcelUtil.class);
    
    private final ExcelFacade excelFacade;

    @Autowired
    public ReactiveExcelUtil(ExcelFacade excelFacade) {
        this.excelFacade = excelFacade;
    }

    /**
     * Process an Excel stream reactively using the default configuration.
     */
    public <T> Flux<T> processExcelReactive(InputStream inputStream, Class<T> beanClass) {
        return processExcelReactive(inputStream, beanClass, defaultConfig());
    }

    /**
     * Process an Excel stream reactively while honouring the provided configuration. The underlying
     * processing now uses ExcelFacade instead of ExcelUtil, providing better architecture and
     * dependency injection support.
     */
    public <T> Flux<T> processExcelReactive(InputStream inputStream,
                                                   Class<T> beanClass,
                                                   ExcelConfig config) {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        Objects.requireNonNull(beanClass, "beanClass must not be null");
        Objects.requireNonNull(config, "config must not be null");

        AtomicBoolean subscribed = new AtomicBoolean(false);

        return Flux.defer(() -> {
            if (!subscribed.compareAndSet(false, true)) {
                return Flux.error(new IllegalStateException("Excel InputStream has already been consumed"));
            }

            return Flux.<T>create(sink -> doProcessReactive(inputStream, beanClass, config, sink),
                            FluxSink.OverflowStrategy.BUFFER)
                    .subscribeOn(Schedulers.boundedElastic());
        });
    }

    /**
     * Process an Excel stream reactively and emit buffered batches. Useful when downstream prefers
     * batch semantics (e.g. database writers) while still benefiting from reactive backpressure.
     */
    public <T> Flux<List<T>> processExcelReactiveBatched(InputStream inputStream,
                                                                Class<T> beanClass,
                                                                ExcelConfig config,
                                                                int batchSize) {
        return processExcelReactive(inputStream, beanClass, config)
                .buffer(batchSize)
                .filter(batch -> !batch.isEmpty());
    }

    /**
     * Bridge reactive processing back to a blocking list for callers that expect the legacy
     * synchronous contract. This method should be used sparingly as it blocks the calling thread.
     */
    public <T> List<T> processExcelReactiveToList(InputStream inputStream,
                                                         Class<T> beanClass,
                                                         ExcelConfig config) {
        return processExcelReactive(inputStream, beanClass, config)
                .collectList()
                .blockOptional()
                .orElse(List.of());
    }

    /**
     * Process an Excel stream reactively and invoke a custom batch consumer while still returning a
     * signal that completes once processing finishes.
     */
    public <T> Mono<Void> processExcelReactive(InputStream inputStream,
                                                      Class<T> beanClass,
                                                      ExcelConfig config,
                                                      Consumer<List<T>> batchConsumer) {
        Objects.requireNonNull(batchConsumer, "batchConsumer must not be null");

        return processExcelReactiveBatched(inputStream, beanClass, config, config.getBatchSize())
                .doOnNext(batchConsumer)
                .then();
    }

    private <T> void doProcessReactive(InputStream inputStream,
                                              Class<T> beanClass,
                                              ExcelConfig config,
                                              FluxSink<T> sink) {
        Consumer<List<T>> emitter = batch -> {
            for (T item : batch) {
                if (sink.isCancelled()) {
                    return;
                }
                sink.next(item);
            }
        };

        try {
            // Use ExcelFacade instead of ExcelUtil for better architecture
            excelFacade.readExcel(inputStream, beanClass, config, emitter);
            sink.complete();
        } catch (ExcelProcessException ex) {
            sink.error(ex);
        } catch (Throwable throwable) {
            sink.error(new ExcelProcessException("Unexpected failure during reactive Excel processing", throwable));
        } finally {
            closeQuietly(inputStream);
        }
    }

    private void closeQuietly(InputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try {
            inputStream.close();
        } catch (IOException ioe) {
            logger.debug("Failed to close Excel InputStream: {}", ioe.getMessage());
        }
    }

    private ExcelConfig defaultConfig() {
        return ExcelConfig.builder().build();
    }
}
