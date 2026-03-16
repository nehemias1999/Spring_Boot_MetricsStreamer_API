package com.metrics_streamer.telemetry_producer.metrics.application.service;

import com.metrics_streamer.telemetry_producer.metrics.application.port.in.ITelemetryStreamUseCase;
import com.metrics_streamer.telemetry_producer.metrics.domain.ServerMetric;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

/**
 * Application service that implements {@link ITelemetryStreamUseCase}.
 *
 * <p>Generates a continuous, non-blocking stream of simulated {@link ServerMetric}
 * snapshots using Project Reactor's {@link Flux#interval(Duration)} operator.
 * Each tick produces one metric with randomised CPU and RAM usage values.</p>
 *
 * <p>This class belongs to the <em>application layer</em> of the hexagonal architecture.
 * It contains only business logic and has no dependency on infrastructure concerns
 * (HTTP, serialisation, etc.).</p>
 *
 * <p>{@code @Slf4j} (Lombok) generates the {@code log} field automatically,
 * replacing the boilerplate {@code LoggerFactory.getLogger(...)} declaration.</p>
 */
@Slf4j
@Service
public class TelemetryService implements ITelemetryStreamUseCase {

    private static final String SERVER_ID = "server-01";
    private static final Random random = new Random();

    /**
     * Creates an infinite cold {@link Flux} that emits one {@link ServerMetric} per second.
     *
     * <p>Pipeline steps:
     * <ol>
     *   <li>{@code Flux.interval} — ticks every second on the parallel scheduler.</li>
     *   <li>{@code .map} — converts each tick index into a new {@link ServerMetric}
     *       with randomised usage values and the current timestamp.</li>
     *   <li>{@code .doOnSubscribe} / {@code .doOnCancel} — lifecycle hooks for
     *       structured logging without side-effectful business logic.</li>
     * </ol>
     * </p>
     *
     * @return a never-completing {@link Flux} of {@link ServerMetric}
     */
    @Override
    public Flux<ServerMetric> streamMetrics() {
        log.info("Initialising telemetry metrics stream for server '{}'", SERVER_ID);

        return Flux.interval(Duration.ofSeconds(1))
                .map(tick -> buildMetric())
                .doOnSubscribe(subscription ->
                        log.info("Subscriber attached — telemetry stream is active for '{}'", SERVER_ID))
                .doOnCancel(() ->
                        log.info("Subscriber disconnected — telemetry stream cancelled for '{}'", SERVER_ID))
                .doOnError(error ->
                        log.error("Error in telemetry stream for '{}': {}", SERVER_ID, error.getMessage(), error));
    }

    /**
     * Builds a single {@link ServerMetric} snapshot with randomised usage values.
     *
     * @return a new {@link ServerMetric} timestamped at the current instant
     */
    private ServerMetric buildMetric() {
        ServerMetric metric = new ServerMetric(
                SERVER_ID,
                random.nextDouble() * 100,
                random.nextDouble() * 100,
                Instant.now()
        );
        log.debug("Emitting metric — serverId='{}', cpu={}%, ram={}%",
                metric.serverId(), metric.cpuUsage(), metric.ramUsage());
        return metric;
    }
}
