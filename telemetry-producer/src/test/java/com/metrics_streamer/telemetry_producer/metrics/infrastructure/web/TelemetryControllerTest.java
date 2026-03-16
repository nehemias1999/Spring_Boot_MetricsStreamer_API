package com.metrics_streamer.telemetry_producer.metrics.infrastructure.web;

import com.metrics_streamer.telemetry_producer.metrics.application.port.in.ITelemetryStreamUseCase;
import com.metrics_streamer.telemetry_producer.metrics.domain.ServerMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration test for {@link TelemetryController}.
 *
 * <p>Boots the full Spring WebFlux application context on a random port so that
 * the real request pipeline (routing, serialisation, content-type negotiation) is
 * exercised end-to-end. {@link WebTestClient} is constructed manually against the
 * random port — Spring Boot 4.x does not auto-configure it in the test context
 * when using {@code RANDOM_PORT}.</p>
 *
 * <p>The {@link TelemetryStreamUseCase} inbound port is replaced by a Mockito mock
 * via {@link MockitoBean} to keep tests fast and deterministic.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("TelemetryController")
class TelemetryControllerTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private ITelemetryStreamUseCase telemetryStreamUseCase;

    private WebTestClient webTestClient;

    /**
     * Rebuilds the {@link WebTestClient} before each test so it targets the
     * correct random port assigned by the test context.
     */
    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/telemetry/stream should return 200 with text/event-stream content type")
    void stream_returnsCorrectContentType() {
        when(telemetryStreamUseCase.streamMetrics()).thenReturn(Flux.empty());

        webTestClient.get()
                .uri("/api/v1/telemetry/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    @Test
    @DisplayName("GET /api/v1/telemetry/stream should emit mocked ServerMetric events")
    void stream_emitsServerMetrics() {
        ServerMetric metric1 = new ServerMetric("server-01", 42.5, 68.3, Instant.parse("2024-01-01T00:00:01Z"));
        ServerMetric metric2 = new ServerMetric("server-01", 55.0, 72.1, Instant.parse("2024-01-01T00:00:02Z"));

        when(telemetryStreamUseCase.streamMetrics()).thenReturn(Flux.just(metric1, metric2));

        List<ServerMetric> received = webTestClient.get()
                .uri("/api/v1/telemetry/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(ServerMetric.class)
                .getResponseBody()
                .take(2)
                .collectList()
                .block();

        assertThat(received).hasSize(2);
        assertThat(received.get(0).serverId()).isEqualTo("server-01");
        assertThat(received.get(0).cpuUsage()).isEqualTo(42.5);
        assertThat(received.get(1).ramUsage()).isEqualTo(72.1);
    }

    @Test
    @DisplayName("GET /api/v1/telemetry/stream should return 200 even when stream is empty")
    void stream_emptyFlux_returnsOk() {
        when(telemetryStreamUseCase.streamMetrics()).thenReturn(Flux.empty());

        webTestClient.get()
                .uri("/api/v1/telemetry/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk();
    }
}
