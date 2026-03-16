package com.metrics_streamer.telemetry_producer.metrics.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TelemetryService}.
 *
 * <p>Uses Reactor's {@link StepVerifier} with virtual time to avoid real-clock delays.
 * Virtual time replaces the parallel scheduler used by {@code Flux.interval},
 * so tests run instantly without sleeping.</p>
 */
@DisplayName("TelemetryService")
class TelemetryServiceTest {

    private TelemetryService telemetryService;

    @BeforeEach
    void setUp() {
        telemetryService = new TelemetryService();
    }

    @Test
    @DisplayName("streamMetrics() should emit the expected number of events over time")
    void streamMetrics_emitsExpectedNumberOfEvents() {
        StepVerifier.withVirtualTime(() -> telemetryService.streamMetrics())
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(3))
                .expectNextCount(3)
                .thenCancel()
                .verify();
    }

    @Test
    @DisplayName("streamMetrics() should emit ServerMetric with valid field values")
    void streamMetrics_emitsMetricWithValidFields() {
        StepVerifier.withVirtualTime(() -> telemetryService.streamMetrics())
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(metric -> {
                    assertThat(metric).isNotNull();
                    assertThat(metric.serverId()).isNotBlank();
                    assertThat(metric.timestamp()).isNotNull();
                    assertThat(metric.cpuUsage()).isBetween(0.0, 100.0);
                    assertThat(metric.ramUsage()).isBetween(0.0, 100.0);
                })
                .thenCancel()
                .verify();
    }

    @Test
    @DisplayName("streamMetrics() should emit metrics with non-null timestamps")
    void streamMetrics_emitsMetricsWithNonNullTimestamps() {
        // Note: virtual time does not advance Instant.now(), so we verify
        // that a timestamp is present rather than checking distinctness.
        StepVerifier.withVirtualTime(() -> telemetryService.streamMetrics())
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(2))
                .assertNext(metric -> assertThat(metric.timestamp()).isNotNull())
                .assertNext(metric -> assertThat(metric.timestamp()).isNotNull())
                .thenCancel()
                .verify();
    }

    @Test
    @DisplayName("streamMetrics() should never complete on its own")
    void streamMetrics_doesNotComplete() {
        StepVerifier.withVirtualTime(() -> telemetryService.streamMetrics())
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(10))
                .expectNextCount(10)
                .thenCancel()
                .verify();
    }
}
