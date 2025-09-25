package com.learnmore.application.utils.feature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Feature toggle component dedicated to Excel processing. It allows the application to roll out the
 * new reactive path gradually while keeping the original synchronous implementation as the default.
 * All flags are thread-safe and can be updated dynamically (e.g. via Actuator endpoints).
 */
@Component
public class ExcelFeatureToggle {

    private static final Logger logger = LoggerFactory.getLogger(ExcelFeatureToggle.class);

    public static final String FEATURE_REACTIVE_PROCESSING = "reactive.processing";

    private final ConcurrentMap<String, AtomicBoolean> featureStates = new ConcurrentHashMap<>();
    private final AtomicInteger reactiveRolloutPercentage = new AtomicInteger(0);

    public ExcelFeatureToggle(
            @Value("${excel.reactive.enabled:false}") boolean reactiveEnabled,
            @Value("${excel.reactive.rollout-percentage:0}") int rolloutPercentage) {
        featureStates.put(FEATURE_REACTIVE_PROCESSING, new AtomicBoolean(reactiveEnabled));
        setReactiveRolloutPercentage(rolloutPercentage);
    }

    /**
     * Decide whether the current request should use the reactive pipeline based on the rollout
     * percentage and optional request identifier. When no identifier is provided the method falls
     * back to the global feature state (all-or-nothing rollout).
     */
    public boolean shouldUseReactive(String requestId) {
        if (!isReactiveEnabled()) {
            return false;
        }

        int percentage = reactiveRolloutPercentage.get();
        if (percentage <= 0) {
            return true; // feature enabled globally but percentage set to 0 -> treat as full enablement
        }
        if (percentage >= 100) {
            return true;
        }

        return Optional.ofNullable(requestId)
                .map(String::hashCode)
                .map(Math::abs)
                .map(hash -> hash % 100 < percentage)
                .orElse(false);
    }

    public boolean isReactiveEnabled() {
        return featureStates.getOrDefault(FEATURE_REACTIVE_PROCESSING, new AtomicBoolean(false)).get();
    }

    public void setReactiveEnabled(boolean enabled) {
        featureStates.computeIfAbsent(FEATURE_REACTIVE_PROCESSING, key -> new AtomicBoolean()).set(enabled);
        logger.info("Excel reactive processing feature toggled to: {}", enabled);
    }

    public int getReactiveRolloutPercentage() {
        return reactiveRolloutPercentage.get();
    }

    public void setReactiveRolloutPercentage(int percentage) {
        int sanitized = Math.max(0, Math.min(percentage, 100));
        reactiveRolloutPercentage.set(sanitized);
        logger.info("Excel reactive rollout percentage set to {}%", sanitized);
    }

    public void toggleFeature(String featureKey, boolean enabled) {
        Objects.requireNonNull(featureKey, "featureKey must not be null");
        featureStates.computeIfAbsent(featureKey, key -> new AtomicBoolean()).set(enabled);
        logger.info("Excel feature '{}' toggled to: {}", featureKey, enabled);
    }

    public boolean isFeatureEnabled(String featureKey) {
        Objects.requireNonNull(featureKey, "featureKey must not be null");
        return featureStates.getOrDefault(featureKey, new AtomicBoolean(false)).get();
    }
}
