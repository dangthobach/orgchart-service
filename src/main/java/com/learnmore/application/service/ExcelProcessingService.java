package com.learnmore.application.service;

import com.learnmore.application.excel.ExcelFacade;
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
    private final ExcelFacade excelFacade;
    private final ReactiveExcelUtil reactiveExcelUtil;

    public ExcelProcessingService(ExcelProcessorAdapter adapter, 
                                 ExcelFacade excelFacade,
                                 ReactiveExcelUtil reactiveExcelUtil) {
        this.adapter = adapter;
        this.excelFacade = excelFacade;
        this.reactiveExcelUtil = reactiveExcelUtil;
    }

    public <T> List<T> processExcelTraditional(InputStream inputStream, Class<T> beanClass) throws ExcelProcessException {
        return excelFacade.readExcel(inputStream, beanClass);
    }

    public <T> List<T> processExcelTraditional(InputStream inputStream,
                                               Class<T> beanClass,
                                               ExcelConfig config) throws ExcelProcessException {
        return excelFacade.readExcel(inputStream, beanClass, config);
    }

    public <T> Flux<T> processExcelReactive(InputStream inputStream, Class<T> beanClass) {
        return reactiveExcelUtil.processExcelReactive(inputStream, beanClass);
    }

    public <T> Flux<T> processExcelReactive(InputStream inputStream,
                                            Class<T> beanClass,
                                            ExcelConfig config) {
        return reactiveExcelUtil.processExcelReactive(inputStream, beanClass, config);
    }

    public <T> ExcelProcessingResponse<T> processExcelHybrid(InputStream inputStream,
                                                             Class<T> beanClass,
                                                             ExcelConfig config,
                                                             ProcessingContext context) throws ExcelProcessException {
        return adapter.process(inputStream, beanClass, config, context);
    }
}
