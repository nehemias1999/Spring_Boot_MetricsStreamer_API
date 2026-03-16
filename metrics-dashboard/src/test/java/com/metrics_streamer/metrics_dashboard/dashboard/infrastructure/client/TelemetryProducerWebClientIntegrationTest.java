package com.metrics_streamer.metrics_dashboard.dashboard.infrastructure.client;

import com.metrics_streamer.metrics_dashboard.dashboard.domain.ServerMetricDto;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link TelemetryProducerWebClient}.
 *
 * <p>Uses {@link MockWebServer} (OkHttp) to start a real local HTTP server that
 * responds with pre-configured SSE payloads. This validates the full HTTP layer:
 * WebClient connection, SSE wire-format parsing, and Jackson deserialisation into
 * {@link ServerMetricDto} — without requiring the actual producer to run.</p>
 *
 * <p>No Spring context is loaded; the WebClient is built directly against the
 * mock server's URL. Tests that cover error propagation inject {@link Retry#max(int)
 * Retry.max(0)} via the package-private constructor to bypass backoff waits and keep
 * the test suite fast.</p>
 */
@DisplayName("TelemetryProducerWebClient (integration)")
class TelemetryProducerWebClientIntegrationTest {

    private MockWebServer mockWebServer;
    private WebClient     webClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    /** Creates a client with production retry (used for happy-path tests). */
    private TelemetryProducerWebClient clientWithDefaultRetry() {
        return new TelemetryProducerWebClient(webClient);
    }

    /** Creates a client with no retries so error tests fail immediately. */
    private TelemetryProducerWebClient clientWithNoRetry() {
        return new TelemetryProducerWebClient(webClient, Retry.max(0));
    }

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchMetricsStream() should deserialise SSE events into ServerMetricDto")
    void fetchMetricsStream_deserialisesTwoEventsFromSSEResponse() {
        String event1 = "{\"serverId\":\"server-01\",\"cpuUsage\":75.0,\"ramUsage\":60.0,"
                + "\"timestamp\":\"2024-01-01T00:00:00Z\"}";
        String event2 = "{\"serverId\":\"server-01\",\"cpuUsage\":90.0,\"ramUsage\":70.0,"
                + "\"timestamp\":\"2024-01-01T00:00:01Z\"}";
        String sseBody = "data:" + event1 + "\n\n" + "data:" + event2 + "\n\n";

        mockWebServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "text/event-stream;charset=UTF-8")
                .setBody(sseBody));

        StepVerifier.create(clientWithDefaultRetry().fetchMetricsStream())
                .assertNext(dto -> {
                    assertThat(dto.serverId()).isEqualTo("server-01");
                    assertThat(dto.cpuUsage()).isEqualTo(75.0);
                    assertThat(dto.ramUsage()).isEqualTo(60.0);
                    assertThat(dto.timestamp()).isNotNull();
                })
                .assertNext(dto -> {
                    assertThat(dto.cpuUsage()).isEqualTo(90.0);
                    assertThat(dto.ramUsage()).isEqualTo(70.0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("fetchMetricsStream() should complete on empty SSE body")
    void fetchMetricsStream_completesOnEmptyBody() {
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "text/event-stream;charset=UTF-8")
                .setBody(""));

        StepVerifier.create(clientWithDefaultRetry().fetchMetricsStream())
                .verifyComplete();
    }

    // ── Resilience tests ───────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchMetricsStream() should error with WebClientResponseException on 5xx (retries disabled)")
    void fetchMetricsStream_errorsOn5xxResponse() {
        // Enqueue one 503 response; no-retry client surfaces the error immediately.
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));

        StepVerifier.create(clientWithNoRetry().fetchMetricsStream())
                .expectErrorSatisfies(error -> {
                    // Retry.max(0) wraps the original error in RetryExhaustedException;
                    // the actual WebClientResponseException is the cause.
                    Throwable root = error.getCause() != null ? error.getCause() : error;
                    assertThat(root).isInstanceOf(WebClientResponseException.class);
                    assertThat(((WebClientResponseException) root).getStatusCode().value())
                            .isEqualTo(503);
                })
                .verify();
    }

    @Test
    @DisplayName("fetchMetricsStream() should retry and succeed after a transient 5xx failure")
    void fetchMetricsStream_retriesAndSucceedsAfterTransientFailure() {
        // First request: transient 503
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));

        // Second request (retry): success
        String sseBody = "data:{\"serverId\":\"server-01\",\"cpuUsage\":88.0,"
                + "\"ramUsage\":55.0,\"timestamp\":\"2024-01-01T00:00:00Z\"}\n\n";
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "text/event-stream;charset=UTF-8")
                .setBody(sseBody));

        // Use minimal backoff so the test doesn't wait 2+ seconds
        Retry fastRetry = Retry.backoff(1, java.time.Duration.ofMillis(50));
        TelemetryProducerWebClient client = new TelemetryProducerWebClient(webClient, fastRetry);

        StepVerifier.create(client.fetchMetricsStream())
                .assertNext(dto -> assertThat(dto.cpuUsage()).isEqualTo(88.0))
                .verifyComplete();
    }
}
