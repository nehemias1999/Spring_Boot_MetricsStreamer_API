package com.metrics_streamer.metrics_dashboard.dashboard.infrastructure.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalized configuration for alert threshold values.
 *
 * <p>Bound from the {@code dashboard.alerts} prefix in {@code application.yaml},
 * allowing thresholds to be changed per environment without recompiling.
 * Example configuration:</p>
 * <pre>
 * dashboard:
 *   alerts:
 *     cpu-threshold: 80.0
 *     ram-threshold: 85.0
 * </pre>
 *
 * <p>{@code @Validated} ensures Bean Validation constraints are enforced at
 * application startup — a misconfigured threshold causes a fast-fail rather
 * than a silent runtime error.</p>
 *
 * @param cpuThreshold CPU usage percentage above which an alert is triggered (exclusive)
 * @param ramThreshold RAM usage percentage above which an alert is triggered (exclusive)
 */
@Validated
@ConfigurationProperties(prefix = "dashboard.alerts")
public record AlertProperties(

        @NotNull(message = "cpu-threshold must not be null")
        @DecimalMin(value = "0.0", message = "cpu-threshold must be >= 0.0")
        @DecimalMax(value = "100.0", message = "cpu-threshold must be <= 100.0")
        Double cpuThreshold,

        @NotNull(message = "ram-threshold must not be null")
        @DecimalMin(value = "0.0", message = "ram-threshold must be >= 0.0")
        @DecimalMax(value = "100.0", message = "ram-threshold must be <= 100.0")
        Double ramThreshold

) {}
