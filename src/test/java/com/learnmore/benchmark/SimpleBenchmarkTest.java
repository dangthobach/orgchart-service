package com.learnmore.benchmark;

import org.junit.jupiter.api.Test;

/**
 * JUnit wrapper for SimpleBenchmark
 */
public class SimpleBenchmarkTest {
    
    @Test
    public void testPerformanceComparison() throws Exception {
        SimpleBenchmark.main(new String[]{});
    }
}