package com.learnmore.application.utils.adapter;

import com.learnmore.application.excel.ExcelFacade;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.feature.ExcelFeatureToggle;
import com.learnmore.application.utils.reactive.ReactiveExcelUtil;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/**
 * Adapter that routes Excel processing calls to either the stable synchronous implementation or the
 * new reactive bridge based on configuration and runtime hints. Existing callers can stick with the
 * traditional API while new endpoints can opt-in to the reactive pathway without impacting the
 * legacy behaviour.
 */
@Component
public class ExcelProcessorAdapter {

    private final ExcelFeatureToggle featureToggle;
    private final ExcelFacade excelFacade;
    private final ReactiveExcelUtil reactiveExcelUtil;

    public ExcelProcessorAdapter(ExcelFeatureToggle featureToggle, 
                                ExcelFacade excelFacade,
                                ReactiveExcelUtil reactiveExcelUtil) {
        this.featureToggle = featureToggle;
        this.excelFacade = excelFacade;
        this.reactiveExcelUtil = reactiveExcelUtil;
    }

    public <T> ExcelProcessingResponse<T> process(InputStream inputStream,
                                                  Class<T> beanClass,
                                                  ExcelConfig config,
                                                  ProcessingContext context) throws ExcelProcessException {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        Objects.requireNonNull(beanClass, "beanClass must not be null");
        ExcelConfig effectiveConfig = config != null ? config : ExcelConfig.builder().build();
        ProcessingContext safeContext = context != null ? context : ProcessingContext.empty();

        boolean forceReactive = safeContext.forceReactive();
        boolean shouldUseReactive = forceReactive
                || safeContext.reactivePreferred()
                || featureToggle.shouldUseReactive(safeContext.requestId());

        if (!shouldUseReactive) {
            // Use ExcelFacade instead of ExcelUtil for better architecture
            List<T> result = excelFacade.readExcel(inputStream, beanClass, effectiveConfig);
            return ExcelProcessingResponse.sync(result, effectiveConfig);
        }

        // Use injected ReactiveExcelUtil instance instead of static method
        Flux<T> flux = reactiveExcelUtil.processExcelReactive(inputStream, beanClass, effectiveConfig);
        return ExcelProcessingResponse.reactive(flux, effectiveConfig);
    }

    public record ExcelProcessingResponse<T>(ProcessingMode mode, List<T> records, Flux<T> stream, ExcelConfig config) {
        public static <T> ExcelProcessingResponse<T> sync(List<T> records, ExcelConfig config) {
            return new ExcelProcessingResponse<>(ProcessingMode.SYNCHRONOUS, records, null, config);
        }

        public static <T> ExcelProcessingResponse<T> reactive(Flux<T> stream, ExcelConfig config) {
            return new ExcelProcessingResponse<>(ProcessingMode.REACTIVE, null, stream, config);
        }

        public boolean isReactive() {
            return mode == ProcessingMode.REACTIVE;
        }

        public boolean isSynchronous() {
            return mode == ProcessingMode.SYNCHRONOUS;
        }
    }

    public enum ProcessingMode {
        SYNCHRONOUS,
        REACTIVE
    }

    public record ProcessingContext(boolean forceReactive,
                                    boolean reactivePreferred,
                                    boolean webFluxEndpoint,
                                    boolean r2dbcDataSource,
                                    int concurrentRequests,
                                    String requestId) {

        public static ProcessingContext empty() {
            return new ProcessingContext(false, false, false, false, 0, null);
        }

        public Builder toBuilder() {
            return new Builder()
                    .forceReactive(forceReactive)
                    .reactivePreferred(reactivePreferred)
                    .webFluxEndpoint(webFluxEndpoint)
                    .r2dbcDataSource(r2dbcDataSource)
                    .concurrentRequests(concurrentRequests)
                    .requestId(requestId);
        }

        public static final class Builder {
            private boolean forceReactive;
            private boolean reactivePreferred;
            private boolean webFluxEndpoint;
            private boolean r2dbcDataSource;
            private int concurrentRequests;
            private String requestId;

            public Builder forceReactive(boolean forceReactive) {
                this.forceReactive = forceReactive;
                return this;
            }

            public Builder reactivePreferred(boolean reactivePreferred) {
                this.reactivePreferred = reactivePreferred;
                return this;
            }

            public Builder webFluxEndpoint(boolean webFluxEndpoint) {
                this.webFluxEndpoint = webFluxEndpoint;
                return this;
            }

            public Builder r2dbcDataSource(boolean r2dbcDataSource) {
                this.r2dbcDataSource = r2dbcDataSource;
                return this;
            }

            public Builder concurrentRequests(int concurrentRequests) {
                this.concurrentRequests = concurrentRequests;
                return this;
            }

            public Builder requestId(String requestId) {
                this.requestId = requestId;
                return this;
            }

            public ProcessingContext build() {
                boolean preferReactive = reactivePreferred || webFluxEndpoint || r2dbcDataSource || concurrentRequests > 10;
                return new ProcessingContext(forceReactive, preferReactive, webFluxEndpoint, r2dbcDataSource, concurrentRequests, requestId);
            }
        }
    }
}
