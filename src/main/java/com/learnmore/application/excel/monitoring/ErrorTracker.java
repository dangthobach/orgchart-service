package com.learnmore.application.excel.monitoring;

import com.learnmore.application.utils.config.ExcelConfig;
import com.learnmore.application.utils.exception.ExcelProcessException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Error tracker with fail-fast and max-errors support.
 */
@Slf4j
public class ErrorTracker {

    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final List<ErrorRecord> errors = new CopyOnWriteArrayList<>();
    private final int maxErrors;
    private final boolean failFast;
    private final boolean strictValidation;

    public ErrorTracker(ExcelConfig config) {
        this.maxErrors = config.getMaxErrorsBeforeAbort();
        this.failFast = config.isFailOnFirstError();
        this.strictValidation = config.isStrictValidation();
    }

    public boolean recordError(int rowNumber, String fieldName, Exception exception) {
        int current = errorCount.incrementAndGet();
        if (errors.size() < 1000) {
            errors.add(new ErrorRecord(rowNumber, fieldName, exception.getClass().getSimpleName(), exception.getMessage()));
        }
        if (failFast) {
            throw new ExcelProcessException("Fail-fast: error at row " + rowNumber + ": " + exception.getMessage(), exception);
        }
        if (strictValidation || current <= 10 || current % 100 == 0) {
            log.warn("Validation/processing error at row {} field {}: {}", rowNumber, fieldName, exception.getMessage());
        }
        if (current >= maxErrors) {
            log.error("Max errors ({}) reached at row {}, aborting", maxErrors, rowNumber);
            return false;
        }
        return true;
    }

    public int getErrorCount() {
        return errorCount.get();
    }

    public ErrorSummary getSummary() {
        ErrorSummary s = new ErrorSummary();
        s.setTotalErrors(errorCount.get());
        s.setDetails(new ArrayList<>(errors));
        s.setMaxErrorsReached(errorCount.get() >= maxErrors);
        return s;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorRecord {
        private int rowNumber;
        private String fieldName;
        private String errorType;
        private String errorMessage;
    }

    @Data
    public static class ErrorSummary {
        private int totalErrors;
        private boolean maxErrorsReached;
        private List<ErrorRecord> details;
    }
}


