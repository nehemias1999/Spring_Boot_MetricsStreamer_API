package com.metrics_streamer.metrics_dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the Metrics Dashboard microservice.
 *
 * <p>This service acts as a reactive consumer of the telemetry stream produced by
 * the {@code telemetry-producer} microservice. It applies alert filtering rules
 * and re-exposes qualifying events as Server-Sent Events to downstream clients.</p>
 *
 * <p>{@code @ConfigurationPropertiesScan} auto-registers all
 * {@code @ConfigurationProperties} classes found in this package and sub-packages
 * (e.g. {@code AlertProperties}), avoiding the need for
 * {@code @EnableConfigurationProperties} on each configuration class.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MetricsDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetricsDashboardApplication.class, args);
    }
}
