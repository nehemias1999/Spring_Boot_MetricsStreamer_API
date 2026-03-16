package com.metrics_streamer.metrics_dashboard.dashboard.application.service;

import com.metrics_streamer.metrics_dashboard.dashboard.application.port.in.IAlertStreamUseCase;
import com.metrics_streamer.metrics_dashboard.dashboard.application.port.out.ITelemetryProducerClient;
import com.metrics_streamer.metrics_dashboard.dashboard.domain.ServerMetricDto;
import com.metrics_streamer.metrics_dashboard.dashboard.infrastructure.config.AlertProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Application service that implements {@link IAlertStreamUseCase}.
 *
 * <p>Consumes the raw telemetry stream via the outbound port {@link ITelemetryProducerClient}
 * and applies two configurable alert rules driven by {@link AlertProperties}:</p>
 * <ul>
 *   <li><b>CPU alert:</b> {@code cpuUsage > cpuThreshold}</li>
 *   <li><b>RAM alert:</b> {@code ramUsage > ramThreshold}</li>
 * </ul>
 * <p>A metric qualifies as an alert if <em>either</em> condition is true.
 * Thresholds are externalised in {@code application.yaml} and validated at
 * startup, so misconfiguration fails fast.</p>
 *
 * <p>This class belongs to the <em>application layer</em> — it contains only
 * business logic and depends exclusively on ports and configuration records,
 * never on HTTP clients or serialisation concerns.</p>
 */
@Slf4j
@Service
public class AlertService implements IAlertStreamUseCase {

    private final ITelemetryProducerClient telemetryProducerClient;
    private final AlertProperties alertProperties;

    /**
     * Constructor injection of both the outbound port and alert configuration.
     *
     * @param telemetryProducerClient outbound port providing the raw telemetry stream
     * @param alertProperties         externalized threshold configuration
     */
    public AlertService(ITelemetryProducerClient telemetryProducerClient,
                        AlertProperties alertProperties) {
        this.telemetryProducerClient = telemetryProducerClient;
        this.alertProperties = alertProperties;
    }

    /**
     * Fetches the raw telemetry stream and filters it to only emit alert events.
     *
     * <p>Pipeline steps:
     * <ol>
     *   <li>{@code fetchMetricsStream()} — opens the upstream from the producer.</li>
     *   <li>{@code .filter()} — applies the CPU and RAM threshold rules; metrics
     *       that do not exceed either threshold are silently discarded.</li>
     *   <li>{@code .doOnNext()} — logs each alert at WARN level, indicating
     *       which resource triggered it (CPU, RAM, or both).</li>
     *   <li>Lifecycle hooks log subscribe/cancel/error events for observability.</li>
     * </ol>
     * </p>
     *
     * @return a {@link Flux} that emits {@link ServerMetricDto} events where
     *         {@code cpuUsage > cpuThreshold} or {@code ramUsage > ramThreshold}
     */
    @Override
    public Flux<ServerMetricDto> streamAlerts() {
        log.info("Initialising alert stream — CPU threshold: {}%, RAM threshold: {}%",
                alertProperties.cpuThreshold(), alertProperties.ramThreshold());

        return telemetryProducerClient.fetchMetricsStream()
                .filter(metric ->
                        metric.cpuUsage() > alertProperties.cpuThreshold() ||
                        metric.ramUsage() > alertProperties.ramThreshold())
                .doOnNext(this::logAlert)
                .doOnSubscribe(subscription ->
                        log.info("Subscriber attached — alert stream is active"))
                .doOnCancel(() ->
                        log.info("Subscriber disconnected — alert stream cancelled"))
                .doOnError(error ->
                        log.error("Error in alert stream: {}", error.getMessage(), error));
    }

    /**
     * Logs an alert event at WARN level, explicitly stating which resource
     * (CPU, RAM, or both) triggered the alert.
     *
     * @param metric the metric snapshot that crossed at least one threshold
     */
    private void logAlert(ServerMetricDto metric) {
        if (metric.cpuUsage() > alertProperties.cpuThreshold()) {
            log.warn("ALERT [CPU] — serverId='{}', cpu={}% (threshold: {}%)",
                    metric.serverId(), metric.cpuUsage(), alertProperties.cpuThreshold());
        }
        if (metric.ramUsage() > alertProperties.ramThreshold()) {
            log.warn("ALERT [RAM] — serverId='{}', ram={}% (threshold: {}%)",
                    metric.serverId(), metric.ramUsage(), alertProperties.ramThreshold());
        }
    }
}
