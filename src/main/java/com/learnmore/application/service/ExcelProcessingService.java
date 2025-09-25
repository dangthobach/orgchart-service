package com.learnmore.application.service;

import com.learnmore.application.utils.ExcelUtil;
import com.learnmore.application.utils.adapter.ExcelProcessorAdapter;
import com.learnmore.application.utils.adapter.ExcelProcessorAdapter.ExcelProcessingResponse;
import com.learnmore.application.utils.adapter.ExcelProcessorAdapter.ProcessingContext;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.reactive.ReactiveExcelUtil;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.util.List;

/**
 * Service facade that exposes synchronous, reactive and hybrid Excel processing entry points. The
 * synchronous methods delegate to the existing stable {@link ExcelUtil} implementation ensuring no
 * behavioural change, while the reactive methods provide an opt-in path for WebFlux/R2DBC consumers.
 */
@Service
public class ExcelProcessingService {

    private final ExcelProcessorAdapter adapter;

    public ExcelProcessingService(ExcelProcessorAdapter adapter) {
        this.adapter = adapter;
    }

    public <T> List<T> processExcelTraditional(InputStream inputStream, Class<T> beanClass) throws ExcelProcessException {
        return ExcelUtil.processExcel(inputStream, beanClass);
    }

    public <T> List<T> processExcelTraditional(InputStream inputStream,
                                               Class<T> beanClass,
                                               ExcelConfig config) throws ExcelProcessException {
        return ExcelUtil.processExcel(inputStream, beanClass, config);
    }

    public <T> Flux<T> processExcelReactive(InputStream inputStream, Class<T> beanClass) {
        return ReactiveExcelUtil.processExcelReactive(inputStream, beanClass);
    }

    public <T> Flux<T> processExcelReactive(InputStream inputStream,
                                            Class<T> beanClass,
                                            ExcelConfig config) {
        return ReactiveExcelUtil.processExcelReactive(inputStream, beanClass, config);
    }

    public <T> ExcelProcessingResponse<T> processExcelHybrid(InputStream inputStream,
                                                             Class<T> beanClass,
                                                             ExcelConfig config,
                                                             ProcessingContext context) throws ExcelProcessException {
        return adapter.process(inputStream, beanClass, config, context);
    }
}
