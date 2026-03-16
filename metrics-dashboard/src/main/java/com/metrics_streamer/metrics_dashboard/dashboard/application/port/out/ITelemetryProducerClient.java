package com.metrics_streamer.metrics_dashboard.dashboard.application.port.out;

import com.metrics_streamer.metrics_dashboard.dashboard.domain.ServerMetricDto;
import reactor.core.publisher.Flux;

/**
 * Outbound port (driven port) for retrieving the raw telemetry stream.
 *
 * <p>Defines the contract that the application core uses to obtain metric events
 * from an external source. The concrete implementation lives in the infrastructure
 * layer ({@code TelemetryProducerWebClient}) and is injected at runtime via
 * Spring's dependency injection — keeping the application service free of any
 * HTTP or WebClient concerns.</p>
 */
public interface ITelemetryProducerClient {

    /**
     * Opens a reactive connection to the telemetry producer and returns an infinite
     * {@link Flux} of raw {@link ServerMetricDto} snapshots.
     *
     * @return a never-completing {@link Flux} of {@link ServerMetricDto} events
     */
    Flux<ServerMetricDto> fetchMetricsStream();
}
