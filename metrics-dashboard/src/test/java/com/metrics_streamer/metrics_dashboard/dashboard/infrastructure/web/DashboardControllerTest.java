package com.metrics_streamer.metrics_dashboard.dashboard.infrastructure.web;

import com.metrics_streamer.metrics_dashboard.dashboard.application.port.in.IAlertStreamUseCase;
import com.metrics_streamer.metrics_dashboard.dashboard.domain.ServerMetricDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Integration test for {@link DashboardController}.
 *
 * <p>Boots the full Spring WebFlux application context on a random port.
 * The {@link IAlertStreamUseCase} inbound port is replaced by a Mockito mock,
 * and the {@link WebClient} bean is mocked to prevent any real HTTP connections
 * to the telemetry producer during the test.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("DashboardController")
class DashboardControllerTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private IAlertStreamUseCase alertStreamUseCase;

    @MockitoBean(name = "producerWebClient")
    private WebClient producerWebClient;

    private WebTestClient webTestClient;

    /**
     * Rebuilds the {@link WebTestClient} before each test pointing to the random port.
     */
    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/alerts should return 200 with text/event-stream content type")
    void alerts_returnsCorrectContentType() {
        when(alertStreamUseCase.streamAlerts()).thenReturn(Flux.empty());

        webTestClient.get()
                .uri("/api/v1/dashboard/alerts")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/alerts should emit alert events")
    void alerts_emitsAlertEvents() {
        ServerMetricDto alert1 = new ServerMetricDto("server-01", 92.3, 75.0, Instant.parse("2024-06-01T12:00:01Z"));
        ServerMetricDto alert2 = new ServerMetricDto("server-01", 88.7, 80.0, Instant.parse("2024-06-01T12:00:02Z"));

        when(alertStreamUseCase.streamAlerts()).thenReturn(Flux.just(alert1, alert2));

        List<ServerMetricDto> received = webTestClient.get()
                .uri("/api/v1/dashboard/alerts")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(ServerMetricDto.class)
                .getResponseBody()
                .take(2)
                .collectList()
                .block();

        assertThat(received).hasSize(2);
        assertThat(received.get(0).cpuUsage()).isEqualTo(92.3);
        assertThat(received.get(1).cpuUsage()).isEqualTo(88.7);
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/alerts should return 200 when alert stream is empty")
    void alerts_emptyStream_returnsOk() {
        when(alertStreamUseCase.streamAlerts()).thenReturn(Flux.empty());

        webTestClient.get()
                .uri("/api/v1/dashboard/alerts")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk();
    }
}
