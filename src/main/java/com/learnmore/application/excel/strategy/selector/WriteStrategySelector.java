package com.learnmore.application.excel.strategy.selector;

import com.learnmore.application.excel.strategy.WriteStrategy;
import com.learnmore.application.utils.config.ExcelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Selector for automatic write strategy selection
 *
 * This component automatically selects the best WriteStrategy based on:
 * 1. Data size (number of records)
 * 2. Cell count (rows * columns)
 * 3. Configuration settings (forceXSSF, forceSXSSF, preferCSVForLargeData)
 * 4. Strategy priority (higher priority = preferred)
 *
 * Selection algorithm:
 * 1. Calculate cell count from data size and estimated columns
 * 2. Filter strategies that support the data size and cell count
 * 3. Sort by priority (descending)
 * 4. Select the highest priority strategy
 * 5. Fallback to first available if none found
 *
 * Strategy priority order:
 * - Priority 20: XSSFWriteStrategy (small files < 50K records)
 * - Priority 15: CSVWriteStrategy (very large files > 2M records)
 * - Priority 10: SXSSFWriteStrategy (medium files 50K - 2M records)
 * - Priority 0: Fallback strategy (should never be needed)
 *
 * Strategy selection criteria:
 * - < 1M cells: XSSF (standard workbook)
 * - 1M - 5M cells: SXSSF (streaming workbook)
 * - > 5M cells: CSV (10x faster)
 *
 * Example usage:
 * <pre>
 * WriteStrategy<User> strategy = writeStrategySelector.selectStrategy(users.size(), config);
 * strategy.execute("output.xlsx", users, config);
 * </pre>
 *
 * This selector enables the Strategy Pattern without requiring clients
 * to know about specific strategy implementations. New strategies can
 * be added by simply implementing WriteStrategy and registering as a
 * Spring component.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WriteStrategySelector {

    // Spring automatically injects all WriteStrategy implementations
    private final List<WriteStrategy<?>> strategies;

    // Estimated columns per row for cell count calculation
    private static final int ESTIMATED_COLUMNS = 20;

    /**
     * Select the best write strategy for the given data and configuration
     *
     * Selection process:
     * 1. Estimate cell count (dataSize * ESTIMATED_COLUMNS)
     * 2. Filter strategies that support the data size and cell count
     * 3. Sort by priority (highest first)
     * 4. Return the highest priority strategy
     * 5. Fallback to first available if none match
     *
     * @param dataSize Number of records to write
     * @param config Excel configuration
     * @param <T> Type of objects to write
     * @return Selected write strategy (never null)
     */
    @SuppressWarnings("unchecked")
    public <T> WriteStrategy<T> selectStrategy(int dataSize, ExcelConfig config) {
        // Estimate cell count (rows * columns)
        long estimatedCellCount = (long) dataSize * ESTIMATED_COLUMNS;

        log.debug("Selecting write strategy for {} records (~{} cells), preferCSV={}",
                 dataSize,
                 estimatedCellCount,
                 config.isPreferCSVForLargeData());

        // Find all strategies that support this data size and configuration
        List<WriteStrategy<?>> supportedStrategies = strategies.stream()
            .filter(strategy -> strategy.supports(dataSize, estimatedCellCount, config))
            .sorted(Comparator.<WriteStrategy<?>>comparingInt(WriteStrategy::getPriority).reversed())
            .toList();

        if (supportedStrategies.isEmpty()) {
            // Fallback to first available strategy if none match
            log.warn("No write strategy found for {} records! Using first available strategy as fallback.",
                    dataSize);
            return (WriteStrategy<T>) strategies.get(0);
        }

        // Select the highest priority strategy
        WriteStrategy<?> selected = supportedStrategies.get(0);

        log.info("Selected write strategy: {} (priority={}, supported={}/{}, dataSize={}, cells=~{})",
                selected.getName(),
                selected.getPriority(),
                supportedStrategies.size(),
                strategies.size(),
                dataSize,
                estimatedCellCount);

        return (WriteStrategy<T>) selected;
    }

    /**
     * Select the best write strategy with explicit cell count
     *
     * Use this method when you know the exact number of columns
     * for more accurate strategy selection.
     *
     * @param dataSize Number of records to write
     * @param cellCount Exact cell count (rows * columns)
     * @param config Excel configuration
     * @param <T> Type of objects to write
     * @return Selected write strategy (never null)
     */
    @SuppressWarnings("unchecked")
    public <T> WriteStrategy<T> selectStrategy(int dataSize, long cellCount, ExcelConfig config) {
        log.debug("Selecting write strategy for {} records ({} cells), preferCSV={}",
                 dataSize,
                 cellCount,
                 config.isPreferCSVForLargeData());

        // Find all strategies that support this data size and configuration
        List<WriteStrategy<?>> supportedStrategies = strategies.stream()
            .filter(strategy -> strategy.supports(dataSize, cellCount, config))
            .sorted(Comparator.<WriteStrategy<?>>comparingInt(WriteStrategy::getPriority).reversed())
            .toList();

        if (supportedStrategies.isEmpty()) {
            // Fallback to first available strategy if none match
            log.warn("No write strategy found for {} records ({} cells)! Using first available strategy as fallback.",
                    dataSize, cellCount);
            return (WriteStrategy<T>) strategies.get(0);
        }

        // Select the highest priority strategy
        WriteStrategy<?> selected = supportedStrategies.get(0);

        log.info("Selected write strategy: {} (priority={}, supported={}/{}, dataSize={}, cells={})",
                selected.getName(),
                selected.getPriority(),
                supportedStrategies.size(),
                strategies.size(),
                dataSize,
                cellCount);

        return (WriteStrategy<T>) selected;
    }

    /**
     * Get all available write strategies
     *
     * Useful for debugging and testing.
     *
     * @return List of all registered write strategies
     */
    public List<WriteStrategy<?>> getAvailableStrategies() {
        return strategies;
    }

    /**
     * Check if a specific strategy is available
     *
     * @param strategyName Strategy name to check
     * @return true if strategy is registered, false otherwise
     */
    public boolean hasStrategy(String strategyName) {
        return strategies.stream()
            .anyMatch(strategy -> strategy.getName().equals(strategyName));
    }

    /**
     * Get strategy by name
     *
     * @param strategyName Strategy name
     * @param <T> Type of objects to write
     * @return Strategy instance or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> WriteStrategy<T> getStrategyByName(String strategyName) {
        return (WriteStrategy<T>) strategies.stream()
            .filter(strategy -> strategy.getName().equals(strategyName))
            .findFirst()
            .orElse(null);
    }

    /**
     * Estimate cell count from data size
     *
     * Uses ESTIMATED_COLUMNS (20) as default column count.
     *
     * @param dataSize Number of records
     * @return Estimated cell count
     */
    public long estimateCellCount(int dataSize) {
        return (long) dataSize * ESTIMATED_COLUMNS;
    }
}
