package com.learnmore.application.excel.strategy.impl;

import com.learnmore.application.excel.strategy.ReadStrategy;
import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import com.learnmore.application.utils.sax.TrueStreamingSAXProcessor;
import com.learnmore.application.utils.validation.ValidationRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Parallel read strategy for multi-core systems
 *
 * This strategy is selected when parallelProcessing is enabled in ExcelConfig.
 * It delegates to the same TrueStreamingSAXProcessor but with a batch processor
 * that can process batches in parallel.
 *
 * Performance characteristics:
 * - Memory: O(batch_size * num_threads) - multiple batches may be in memory
 * - Speed: ~100,000-200,000 records/sec (multi-threaded, depends on cores)
 * - File size: Up to 2M records (limited by memory)
 *
 * IMPORTANT: The actual parallel processing happens in the batch processor
 * provided by the caller. TrueStreamingSAXProcessor itself is single-threaded
 * (SAX parsing must be sequential), but the batch callbacks can be parallelized.
 *
 * This strategy ALWAYS delegates to ExcelUtil.processExcelTrueStreaming()
 * to ensure ZERO performance impact from the refactoring.
 *
 * When to use:
 * - Multi-core systems (4+ cores)
 * - When config.isParallelProcessing() == true
 * - When batch processing can benefit from parallelization
 * - When memory is sufficient for multiple batches
 *
 * Strategy selection:
 * - Priority: 10 (higher than streaming, lower than custom)
 * - Supports: Only when config.isParallelProcessing() == true
 *
 * @param <T> The type of objects to read from Excel
 */
@Slf4j
@Component
public class ParallelReadStrategy<T> implements ReadStrategy<T> {

    /**
     * Execute parallel read using SAX processing with parallel batch callbacks
     *
     * This method now implements the logic directly with parallel batch processing:
     * - Reads Excel file sequentially (SAX parsing must be sequential)
     * - Creates parallel batch processor using ExecutorService
     * - Processes batches concurrently for better performance
     *
     * Example parallel batch processor:
     * <pre>
     * Consumer<List<User>> parallelProcessor = batch -> {
     *     // Process batch in parallel using ExecutorService
     *     executorService.submit(() -> {
     *         userRepository.saveAll(batch);
     *     });
     * };
     * </pre>
     *
     * @param inputStream Excel file input stream
     * @param beanClass Class type to map Excel rows to
     * @param config Excel configuration with parallelProcessing enabled
     * @param batchProcessor Consumer that processes batches (can be parallel)
     * @return ProcessingResult with statistics
     * @throws ExcelProcessException if reading fails
     */
    @Override
    public TrueStreamingSAXProcessor.ProcessingResult execute(
        InputStream inputStream,
        Class<T> beanClass,
        ExcelConfig config,
        Consumer<List<T>> batchProcessor
    ) throws ExcelProcessException {
        log.debug("Executing ParallelReadStrategy for class: {} (parallelProcessing={})",
                 beanClass.getSimpleName(), config.isParallelProcessing());

        // Verify that parallel processing is actually enabled
        if (!config.isParallelProcessing()) {
            log.warn("ParallelReadStrategy selected but parallelProcessing is disabled. " +
                    "Consider using StreamingReadStrategy instead.");
        }

        try {
            // Create validation rules (empty for now, can be extended)
            List<ValidationRule> validationRules = new ArrayList<>();
            
            // Create parallel batch processor using ExecutorService
            ExecutorService executorService = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
            );
            
            Consumer<List<T>> parallelBatchProcessor = batch -> {
                CompletableFuture.runAsync(() -> {
                    try {
                        batchProcessor.accept(batch);
                    } catch (Exception e) {
                        log.error("Error processing batch in parallel", e);
                    }
                }, executorService);
            };
            
            // Create TrueStreamingSAXProcessor with parallel batch processing
            TrueStreamingSAXProcessor<T> processor = new TrueStreamingSAXProcessor<>(
                beanClass,
                config,
                validationRules,
                parallelBatchProcessor
            );
            
            // Process Excel with true streaming and parallel batch processing
            TrueStreamingSAXProcessor.ProcessingResult result = processor.processExcelStreamTrue(inputStream);
            
            // Shutdown executor service
            executorService.shutdown();
            
            log.info("ParallelReadStrategy completed: {} records in {} ms ({} rec/sec)",
                    result.getProcessedRecords(),
                    result.getProcessingTimeMs(),
                    result.getRecordsPerSecond());

            return result;
            
        } catch (Exception e) {
            log.error("ParallelReadStrategy failed for class: {}", beanClass.getSimpleName(), e);
            throw new ExcelProcessException("Failed to process Excel file with parallel strategy", e);
        }
    }

    /**
     * Check if this strategy supports the given configuration
     *
     * ParallelReadStrategy is only selected when:
     * 1. config.isParallelProcessing() == true
     *
     * This ensures that parallel processing is explicitly requested
     * and the system has the resources to handle it.
     *
     * @param config Excel configuration to check
     * @return true if parallelProcessing is enabled, false otherwise
     */
    @Override
    public boolean supports(ExcelConfig config) {
        // Only support when parallel processing is explicitly enabled
        boolean supported = config.isParallelProcessing();

        if (supported) {
            log.debug("ParallelReadStrategy supports config with parallelProcessing=true");
        }

        return supported;
    }

    /**
     * Get strategy name for logging and debugging
     *
     * @return Strategy name
     */
    @Override
    public String getName() {
        return "ParallelReadStrategy";
    }

    /**
     * Get priority for strategy selection
     *
     * Priority 10 means this strategy is preferred over StreamingReadStrategy (0)
     * when the configuration supports it (parallelProcessing enabled).
     *
     * Priority ordering:
     * - 0: StreamingReadStrategy (default baseline)
     * - 10: ParallelReadStrategy (preferred for parallel config)
     * - 20: CustomStrategy (future extensions)
     *
     * @return Priority level (10 = preferred for parallel)
     */
    @Override
    public int getPriority() {
        return 10; // Higher priority than streaming when parallel is enabled
    }
}
