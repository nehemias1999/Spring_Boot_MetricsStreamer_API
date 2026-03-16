package com.metrics_streamer.metrics_dashboard.dashboard.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Infrastructure configuration for reactive HTTP clients.
 *
 * <p>Defines a {@link WebClient} bean pre-configured with the telemetry producer's
 * base URL. The URL is externalised via {@code application.yaml} so it can be
 * overridden per environment without code changes.</p>
 *
 * <p>The {@link WebClient.Builder} is injected from the Spring context rather than
 * constructed directly with {@code WebClient.builder()}. This is essential so that
 * Spring Boot's auto-configured customizers — including the Micrometer Tracing
 * propagation customizer — are applied before the bean is built, ensuring that
 * {@code traceId} and {@code spanId} are forwarded in outbound HTTP headers.</p>
 */
@Configuration
public class WebClientConfig {

    /**
     * Base URL of the {@code telemetry-producer} microservice, injected from
     * {@code telemetry.producer.base-url} in {@code application.yaml}.
     */
    @Value("${telemetry.producer.base-url}")
    private String producerBaseUrl;

    /**
     * Creates a {@link WebClient} instance bound to the telemetry producer's base URL.
     *
     * <p>Named {@code producerWebClient} to avoid a bean name collision with the
     * {@code TelemetryProducerWebClient} component registered under the same camelCase
     * name. Consumers qualify this bean by name at injection points.</p>
     *
     * @param webClientBuilder the auto-configured builder provided by Spring Boot,
     *                         pre-loaded with tracing and observability customizers
     * @return a configured {@link WebClient} ready for SSE consumption
     */
    @Bean
    public WebClient producerWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .baseUrl(producerBaseUrl)
                .build();
    }
}
