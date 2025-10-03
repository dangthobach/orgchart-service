package com.learnmore.application.excel.strategy.selector;

import com.learnmore.application.excel.strategy.ReadStrategy;
import com.learnmore.application.utils.config.ExcelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Selector for automatic read strategy selection
 *
 * This component automatically selects the best ReadStrategy based on:
 * 1. Configuration settings (parallelProcessing, etc.)
 * 2. Strategy priority (higher priority = preferred)
 * 3. Strategy support (must support the config)
 *
 * Selection algorithm:
 * 1. Filter strategies that support the configuration
 * 2. Sort by priority (descending)
 * 3. Select the highest priority strategy
 * 4. Fallback to StreamingReadStrategy if none found (should never happen)
 *
 * Strategy priority order:
 * - Priority 20+: Custom strategies (future extensions)
 * - Priority 10: ParallelReadStrategy (when parallel enabled)
 * - Priority 0: StreamingReadStrategy (always available fallback)
 *
 * Example usage:
 * <pre>
 * ReadStrategy<User> strategy = readStrategySelector.selectStrategy(config);
 * ProcessingResult result = strategy.execute(inputStream, User.class, config, batchProcessor);
 * </pre>
 *
 * This selector enables the Strategy Pattern without requiring clients
 * to know about specific strategy implementations. New strategies can
 * be added by simply implementing ReadStrategy and registering as a
 * Spring component.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadStrategySelector {

    // Spring automatically injects all ReadStrategy implementations
    private final List<ReadStrategy<?>> strategies;

    /**
     * Select the best read strategy for the given configuration
     *
     * Selection process:
     * 1. Filter strategies that support the configuration
     * 2. Sort by priority (highest first)
     * 3. Return the highest priority strategy
     * 4. Fallback to first available if none match (should never happen)
     *
     * @param config Excel configuration
     * @param <T> Type of objects to read
     * @return Selected read strategy (never null)
     */
    @SuppressWarnings("unchecked")
    public <T> ReadStrategy<T> selectStrategy(ExcelConfig config) {
        log.debug("Selecting read strategy for config: parallelProcessing={}",
                 config.isParallelProcessing());

        // Find all strategies that support this configuration
        List<ReadStrategy<?>> supportedStrategies = strategies.stream()
            .filter(strategy -> strategy.supports(config))
            .sorted(Comparator.<ReadStrategy<?>>comparingInt(ReadStrategy::getPriority).reversed())
            .toList();

        if (supportedStrategies.isEmpty()) {
            // This should never happen since StreamingReadStrategy always supports any config
            log.error("No read strategy found for config! Using first available strategy as fallback.");
            return (ReadStrategy<T>) strategies.get(0);
        }

        // Select the highest priority strategy
        ReadStrategy<?> selected = supportedStrategies.get(0);

        log.info("Selected read strategy: {} (priority={}, supported={}/{})",
                selected.getName(),
                selected.getPriority(),
                supportedStrategies.size(),
                strategies.size());

        return (ReadStrategy<T>) selected;
    }

    /**
     * Get all available read strategies
     *
     * Useful for debugging and testing.
     *
     * @return List of all registered read strategies
     */
    public List<ReadStrategy<?>> getAvailableStrategies() {
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
     * @param <T> Type of objects to read
     * @return Strategy instance or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> ReadStrategy<T> getStrategyByName(String strategyName) {
        return (ReadStrategy<T>) strategies.stream()
            .filter(strategy -> strategy.getName().equals(strategyName))
            .findFirst()
            .orElse(null);
    }
}
