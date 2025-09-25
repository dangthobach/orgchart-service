package com.learnmore.application.utils.writer;

import com.learnmore.application.utils.exception.ExcelProcessException;
import java.io.OutputStream;
import java.util.List;

/**
 * Common interface for all Excel writers
 * Provides unified API for different writing strategies
 */
public interface ExcelWriter<T> {
    
    /**
     * Write data to Excel file with specified output stream
     * @param data List of objects to write
     * @param outputStream Target output stream
     * @return Writing statistics and result
     * @throws ExcelProcessException if writing fails
     */
    WritingResult write(List<T> data, OutputStream outputStream) throws ExcelProcessException;
    
    /**
     * Get current writing statistics
     * @return Current statistics
     */
    WritingResult getStatistics();
    
    /**
     * Get writer strategy name
     * @return Strategy identifier
     */
    default String getStrategyName() {
        return this.getClass().getSimpleName();
    }
}