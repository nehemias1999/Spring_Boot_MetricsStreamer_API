package com.metrics_streamer.telemetry_producer.metrics.infrastructure.web;

import com.metrics_streamer.telemetry_producer.metrics.application.port.in.ITelemetryStreamUseCase;
import com.metrics_streamer.telemetry_producer.metrics.domain.ServerMetric;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * REST adapter (driving/inbound side) that exposes the telemetry stream
 * as a Server-Sent Events (SSE) endpoint.
 *
 * <p>Belongs to the <em>infrastructure layer</em> of the hexagonal architecture.
 * It depends on the inbound port {@link ITelemetryStreamUseCase} — never on the
 * concrete service — preserving the dependency inversion principle.</p>
 *
 * <p>{@code @Slf4j} (Lombok) generates the {@code log} field automatically,
 * replacing the boilerplate {@code LoggerFactory.getLogger(...)} declaration.</p>
 *
 * <p>Base path: {@code /api/v1/telemetry}</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryController {

    private final ITelemetryStreamUseCase telemetryStreamUseCase;

    /**
     * Constructor injection ensures the dependency is mandatory and simplifies testing.
     *
     * @param telemetryStreamUseCase inbound port providing the reactive metrics stream
     */
    public TelemetryController(ITelemetryStreamUseCase telemetryStreamUseCase) {
        this.telemetryStreamUseCase = telemetryStreamUseCase;
    }

    /**
     * SSE endpoint that streams server metrics to the connected client.
     *
     * <p>Each event is a JSON-serialised {@link ServerMetric} emitted once per second.
     * The connection stays open indefinitely until the client disconnects or the
     * server cancels the subscription.</p>
     *
     * <p>{@code produces = MediaType.TEXT_EVENT_STREAM_VALUE} instructs Spring WebFlux
     * to serialise the {@link Flux} as the {@code text/event-stream} MIME type,
     * which is the standard format for Server-Sent Events.</p>
     *
     * @return an infinite {@link Flux} of {@link ServerMetric} streamed as SSE
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerMetric> streamTelemetry() {
        log.info("GET /api/v1/telemetry/stream — new client connected");
        return telemetryStreamUseCase.streamMetrics();
    }
}
