package com.metrics_streamer.metrics_dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Smoke test that verifies the full Spring application context loads without errors.
 *
 * <p>The {@link WebClient} bean is mocked to prevent the application from attempting
 * a real HTTP connection to the telemetry producer during context startup.</p>
 */
@SpringBootTest
class MetricsDashboardApplicationTests {

    @MockitoBean(name = "producerWebClient")
    WebClient producerWebClient;

    @Test
    void contextLoads() {
    }
}
