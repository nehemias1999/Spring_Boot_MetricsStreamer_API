package com.metrics_streamer.metrics_dashboard.dashboard.domain;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Data Transfer Object representing a server metric snapshot received from the
 * {@code telemetry-producer} microservice.
 *
 * <p>The field names and types mirror the JSON structure emitted by the producer's
 * SSE endpoint, so Jackson can deserialise the stream without any custom mapping.</p>
 *
 * <p>Bean Validation constraints are declared on the record components and serve
 * as a defensive contract: any metric arriving with out-of-range values is rejected
 * before it reaches business logic.</p>
 *
 * @param serverId  unique identifier of the originating server
 * @param cpuUsage  CPU usage percentage in the range [0.0, 100.0]
 * @param ramUsage  RAM usage percentage in the range [0.0, 100.0]
 * @param timestamp the instant at which the metric was captured by the producer
 */
public record ServerMetricDto(

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
