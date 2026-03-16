package com.metrics_streamer.telemetry_producer.metrics.application.port.in;

import com.metrics_streamer.telemetry_producer.metrics.domain.ServerMetric;
import reactor.core.publisher.Flux;

/**
 * Inbound port (driving port) for the telemetry streaming use case.
 *
 * <p>Defines the contract that the application core exposes to the outside world.
 * Infrastructure adapters (e.g. REST controllers) depend on this interface,
 * never on concrete service implementations — keeping the hexagonal boundary intact.</p>
 *
 * <p>Naming convention: prefixed with {@code I} to distinguish port interfaces
 * from their implementations across the hexagonal layers.</p>
 */
public interface ITelemetryStreamUseCase {

    /**
     * Returns a cold, infinite {@link Flux} that emits a new {@link ServerMetric}
     * at a regular interval for as long as there is an active subscriber.
     *
     * @return a never-completing {@link Flux} of {@link ServerMetric} events
     */
    Flux<ServerMetric> streamMetrics();
}
