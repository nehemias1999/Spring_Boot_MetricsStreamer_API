package com.metrics_streamer.metrics_dashboard.dashboard.infrastructure.web;

import com.metrics_streamer.metrics_dashboard.dashboard.application.port.in.IAlertStreamUseCase;
import com.metrics_streamer.metrics_dashboard.dashboard.domain.ServerMetricDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * REST adapter (driving/inbound side) that exposes the filtered alert stream
 * as a Server-Sent Events (SSE) endpoint.
 *
 * <p>Belongs to the <em>infrastructure layer</em> of the hexagonal architecture.
 * It depends on the inbound port {@link IAlertStreamUseCase} — never on the
 * concrete service — preserving the dependency inversion principle.</p>
 *
 * <p>Base path: {@code /api/v1/dashboard}</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final IAlertStreamUseCase alertStreamUseCase;

    /**
     * Constructor injection ensures the dependency is mandatory and simplifies testing.
     *
     * @param alertStreamUseCase inbound port providing the filtered alert stream
     */
    public DashboardController(IAlertStreamUseCase alertStreamUseCase) {
        this.alertStreamUseCase = alertStreamUseCase;
    }

    /**
     * SSE endpoint that streams high-CPU alert events to the connected client.
     *
     * <p>Only {@link ServerMetricDto} events that passed the business-rule filter
     * in the application layer are emitted here. The connection stays open
     * indefinitely until the client disconnects.</p>
     *
     * @return a {@link Flux} of {@link ServerMetricDto} alert events streamed as SSE
     */
    @GetMapping(path = "/alerts", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerMetricDto> streamAlerts() {
        log.info("GET /api/v1/dashboard/alerts — new client connected");
        return alertStreamUseCase.streamAlerts();
    }
}
