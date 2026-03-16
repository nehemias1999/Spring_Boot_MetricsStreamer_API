package com.metrics_streamer.metrics_dashboard.dashboard.application.service;

import com.metrics_streamer.metrics_dashboard.dashboard.application.port.out.ITelemetryProducerClient;
import com.metrics_streamer.metrics_dashboard.dashboard.domain.ServerMetricDto;
import com.metrics_streamer.metrics_dashboard.dashboard.infrastructure.config.AlertProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AlertService}.
 *
 * <p>Verifies both the CPU and RAM alert filtering rules in isolation by mocking
 * the {@link ITelemetryProducerClient} outbound port with Mockito and asserting
 * the reactive pipeline's behaviour with {@link StepVerifier}.</p>
 *
 * <p>Alert thresholds are set to standard values via a direct {@link AlertProperties}
 * instance — no Spring context is needed for these pure business logic tests.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlertService")
class AlertServiceTest {

    private static final double CPU_THRESHOLD = 80.0;
    private static final double RAM_THRESHOLD = 85.0;

    @Mock
    private ITelemetryProducerClient telemetryProducerClient;

    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(
                telemetryProducerClient,
                new AlertProperties(CPU_THRESHOLD, RAM_THRESHOLD));
    }

    // ── CPU threshold tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("streamAlerts() should only emit metrics where cpuUsage > 80.0")
    void streamAlerts_cpuFilter_blocksAtAndBelowThreshold() {
        ServerMetricDto belowThreshold = new ServerMetricDto("server-01", 50.0, 60.0, Instant.now());
        ServerMetricDto atThreshold    = new ServerMetricDto("server-01", 80.0, 60.0, Instant.now());
        ServerMetricDto aboveThreshold = new ServerMetricDto("server-01", 95.5, 60.0, Instant.now());

        when(telemetryProducerClient.fetchMetricsStream())
                .thenReturn(Flux.just(belowThreshold, atThreshold, aboveThreshold));

        StepVerifier.create(alertService.streamAlerts())
                .assertNext(metric -> assertThat(metric.cpuUsage()).isEqualTo(95.5))
                .verifyComplete();
    }

    // ── RAM threshold tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("streamAlerts() should emit metrics where ramUsage > 85.0 even if cpu is normal")
    void streamAlerts_ramFilter_emitsHighRamMetric() {
        ServerMetricDto normalRam = new ServerMetricDto("server-01", 40.0, 70.0, Instant.now());
        ServerMetricDto highRam   = new ServerMetricDto("server-01", 40.0, 92.0, Instant.now());

        when(telemetryProducerClient.fetchMetricsStream())
                .thenReturn(Flux.just(normalRam, highRam));

        StepVerifier.create(alertService.streamAlerts())
                .assertNext(metric -> {
                    assertThat(metric.ramUsage()).isEqualTo(92.0);
                    assertThat(metric.cpuUsage()).isEqualTo(40.0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("streamAlerts() should emit metric when BOTH cpu and ram exceed thresholds")
    void streamAlerts_emitsWhenBothThresholdsExceeded() {
        ServerMetricDto bothHigh = new ServerMetricDto("server-01", 91.0, 90.0, Instant.now());

        when(telemetryProducerClient.fetchMetricsStream())
                .thenReturn(Flux.just(bothHigh));

        StepVerifier.create(alertService.streamAlerts())
                .assertNext(metric -> {
                    assertThat(metric.cpuUsage()).isEqualTo(91.0);
                    assertThat(metric.ramUsage()).isEqualTo(90.0);
                })
                .verifyComplete();
    }

    // ── Combined and edge cases ────────────────────────────────────────────────

    @Test
    @DisplayName("streamAlerts() should emit nothing when all metrics are below both thresholds")
    void streamAlerts_emitsNothingWhenAllBelowBothThresholds() {
        ServerMetricDto low  = new ServerMetricDto("server-01", 20.0, 40.0, Instant.now());
        ServerMetricDto mid  = new ServerMetricDto("server-01", 79.9, 84.9, Instant.now());
        ServerMetricDto edge = new ServerMetricDto("server-01", 80.0, 85.0, Instant.now());

        when(telemetryProducerClient.fetchMetricsStream())
                .thenReturn(Flux.just(low, mid, edge));

        StepVerifier.create(alertService.streamAlerts())
                .verifyComplete();
    }

    @Test
    @DisplayName("streamAlerts() should emit all metrics when all exceed at least one threshold")
    void streamAlerts_emitsAllWhenAllExceedAtLeastOneThreshold() {
        ServerMetricDto highCpu = new ServerMetricDto("server-01", 85.0, 50.0, Instant.now());
        ServerMetricDto highRam = new ServerMetricDto("server-01", 30.0, 90.0, Instant.now());
        ServerMetricDto both    = new ServerMetricDto("server-01", 99.0, 99.0, Instant.now());

        when(telemetryProducerClient.fetchMetricsStream())
                .thenReturn(Flux.just(highCpu, highRam, both));

        StepVerifier.create(alertService.streamAlerts())
                .expectNextCount(3)
                .verifyComplete();
    }

    @Test
    @DisplayName("streamAlerts() should emit an empty stream when upstream is empty")
    void streamAlerts_emptyUpstream_emitsNothing() {
        when(telemetryProducerClient.fetchMetricsStream())
                .thenReturn(Flux.empty());

        StepVerifier.create(alertService.streamAlerts())
                .verifyComplete();
    }

    @Test
    @DisplayName("streamAlerts() should propagate errors from the upstream client")
    void streamAlerts_propagatesUpstreamError() {
        RuntimeException upstreamError = new RuntimeException("Producer connection refused");

        when(telemetryProducerClient.fetchMetricsStream())
                .thenReturn(Flux.error(upstreamError));

        StepVerifier.create(alertService.streamAlerts())
                .expectErrorMatches(error ->
                        error instanceof RuntimeException &&
                        error.getMessage().equals("Producer connection refused"))
                .verify();
    }
}
