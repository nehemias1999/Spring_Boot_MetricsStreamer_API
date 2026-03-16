package com.metrics_streamer.metrics_dashboard.dashboard.infrastructure.client;

import com.metrics_streamer.metrics_dashboard.dashboard.application.port.out.ITelemetryProducerClient;
import com.metrics_streamer.metrics_dashboard.dashboard.domain.ServerMetricDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Outbound adapter that implements {@link ITelemetryProducerClient} using Spring's
 * reactive {@link WebClient}.
 *
 * <p>Belongs to the <em>infrastructure layer</em> of the hexagonal architecture.
 * It translates the outbound port contract into a concrete HTTP SSE connection
 * to the {@code telemetry-producer} microservice's stream endpoint.</p>
 *
 * <h3>Resilience strategy</h3>
 * <ul>
 *   <li><b>Element timeout:</b> if no event is received within
 *       {@link #ELEMENT_TIMEOUT} the stream errors immediately, preventing
 *       a silent stall when the producer stops emitting.</li>
 *   <li><b>Retry with exponential backoff:</b> transient failures (network blips,
 *       producer restarts) trigger up to {@link #MAX_RETRY_ATTEMPTS} retries.
 *       Wait time starts at {@link #RETRY_INITIAL_BACKOFF} and doubles each
 *       attempt up to {@link #RETRY_MAX_BACKOFF}, with jitter to avoid thundering
 *       herds when multiple consumers reconnect simultaneously.</li>
 * </ul>
 *
 * <h3>Testability</h3>
 * <p>The {@link Retry} spec is injected via a package-private constructor, allowing
 * tests to substitute {@code Retry.max(0)} for instant failure without waiting for
 * real backoff durations.</p>
 */
@Slf4j
@Component
public class TelemetryProducerWebClient implements ITelemetryProducerClient {

    private static final String METRICS_STREAM_PATH = "/api/v1/telemetry/stream";

    /** Maximum time to wait for the next SSE event before considering the stream stalled. */
    static final Duration ELEMENT_TIMEOUT       = Duration.ofSeconds(10);

    /** Maximum number of retry attempts before giving up. */
    static final int      MAX_RETRY_ATTEMPTS    = 3;

    /** Initial wait time before the first retry. */
    static final Duration RETRY_INITIAL_BACKOFF = Duration.ofSeconds(2);

    /** Maximum wait time between retries (exponential backoff ceiling). */
    static final Duration RETRY_MAX_BACKOFF     = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final Retry     retrySpec;

    /**
     * Primary constructor used by Spring. Configures the production retry spec
     * with exponential backoff and jitter.
     *
     * @param producerWebClient WebClient bound to the producer's base URL
     */
    @Autowired
    public TelemetryProducerWebClient(@Qualifier("producerWebClient") WebClient producerWebClient) {
        this(producerWebClient, buildDefaultRetry());
    }

    /**
     * Package-private constructor for testing. Accepts a custom {@link Retry} spec
     * so tests can pass {@code Retry.max(0)} to fail immediately without backoff delays.
     *
     * @param webClient WebClient to use
     * @param retrySpec the retry policy to apply to the stream
     */
    TelemetryProducerWebClient(WebClient webClient, Retry retrySpec) {
        this.webClient = webClient;
        this.retrySpec = retrySpec;
    }

    /**
     * Opens an SSE connection to the telemetry producer and streams the response
     * as a reactive {@link Flux} of {@link ServerMetricDto}.
     *
     * <p>Pipeline steps:
     * <ol>
     *   <li>GET request with {@code Accept: text/event-stream}.</li>
     *   <li>{@code bodyToFlux} — Jackson deserialises each SSE {@code data:} field
     *       into a {@link ServerMetricDto}.</li>
     *   <li>{@code .timeout} — errors the stream if no element arrives within
     *       {@link #ELEMENT_TIMEOUT}, preventing silent stalls.</li>
     *   <li>{@code .retryWhen} — on any error, waits with the configured
     *       {@link #retrySpec} before reopening the connection.</li>
     * </ol>
     * </p>
     *
     * @return a {@link Flux} of {@link ServerMetricDto} events
     */
    @Override
    public Flux<ServerMetricDto> fetchMetricsStream() {
        log.info("Opening SSE connection to telemetry producer at '{}'", METRICS_STREAM_PATH);

        return webClient.get()
                .uri(METRICS_STREAM_PATH)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(ServerMetricDto.class)
                .timeout(ELEMENT_TIMEOUT)
                .retryWhen(retrySpec)
                .doOnSubscribe(subscription ->
                        log.info("SSE connection established with telemetry producer"))
                .doOnCancel(() ->
                        log.info("SSE connection to telemetry producer cancelled"))
                .doOnError(error ->
                        log.error("SSE connection to telemetry producer failed permanently: {}",
                                error.getMessage(), error));
    }

    /**
     * Builds the production retry specification: exponential backoff with jitter,
     * logging a warning before each attempt.
     *
     * @return the default {@link Retry} spec used in production
     */
    private static Retry buildDefaultRetry() {
        return Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_INITIAL_BACKOFF)
                .maxBackoff(RETRY_MAX_BACKOFF)
                .jitter(0.5)
                .doBeforeRetry(signal -> log.warn(
                        "SSE connection lost — retrying ({}/{}) after backoff. Cause: {}",
                        signal.totalRetries() + 1,
                        MAX_RETRY_ATTEMPTS,
                        signal.failure().getMessage()));
    }
}
