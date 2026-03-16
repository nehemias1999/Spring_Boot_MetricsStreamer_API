package com.metrics_streamer.telemetry_producer.metrics.domain;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Domain model representing a single snapshot of server performance metrics.
 *
 * <p>Implemented as a Java record to enforce immutability and provide
 * value-based equality semantics — appropriate for pure domain data.</p>
 *
 * <p>Bean Validation constraints are declared on the record components so they
 * apply to both the canonical constructor parameters and the generated accessor
 * methods. Validation is triggered programmatically via {@code Validator} or
 * declaratively with {@code @Valid} on method parameters.</p>
 *
 * @param serverId  unique identifier of the server being monitored
 * @param cpuUsage  CPU usage percentage in the range [0.0, 100.0]
 * @param ramUsage  RAM usage percentage in the range [0.0, 100.0]
 * @param timestamp the instant at which the metric was captured
 */
public record ServerMetric(

        @NotBlank(message = "serverId must not be blank")
        String serverId,

        @NotNull(message = "cpuUsage must not be null")
        @DecimalMin(value = "0.0", message = "cpuUsage must be >= 0.0")
        @DecimalMax(value = "100.0", message = "cpuUsage must be <= 100.0")
        Double cpuUsage,

        @NotNull(message = "ramUsage must not be null")
        @DecimalMin(value = "0.0", message = "ramUsage must be >= 0.0")
        @DecimalMax(value = "100.0", message = "ramUsage must be <= 100.0")
        Double ramUsage,

        @NotNull(message = "timestamp must not be null")
        Instant timestamp

) {}
