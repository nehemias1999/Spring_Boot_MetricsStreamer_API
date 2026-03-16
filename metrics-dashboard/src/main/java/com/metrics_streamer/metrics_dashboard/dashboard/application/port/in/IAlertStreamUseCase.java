package com.metrics_streamer.metrics_dashboard.dashboard.application.port.in;

import com.metrics_streamer.metrics_dashboard.dashboard.domain.ServerMetricDto;
import reactor.core.publisher.Flux;

/**
 * Inbound port (driving port) for the alert streaming use case.
 *
 * <p>Defines the contract that the application core exposes to the outside world.
 * Infrastructure adapters (e.g. REST controllers) depend on this interface,
 * never on concrete service implementations — keeping the hexagonal boundary intact.</p>
 */
public interface IAlertStreamUseCase {

    /**
     * Returns a filtered {@link Flux} that only emits {@link ServerMetricDto} events
     * that qualify as alerts according to the configured business rules
     * (e.g. CPU usage above the alert threshold).
     *
     * @return a {@link Flux} of {@link ServerMetricDto} alert events
     */
    Flux<ServerMetricDto> streamAlerts();
}
