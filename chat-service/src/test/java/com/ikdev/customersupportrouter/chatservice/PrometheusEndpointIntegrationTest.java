package com.ikdev.customersupportrouter.chatservice;

import com.ikdev.customersupportrouter.chatservice.dto.CreateMessageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that verifies the Prometheus metrics endpoint is exposed
 * and contains custom metrics from chat-service.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class PrometheusEndpointIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.3.1"));

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void prometheusEndpoint_exposesCustomMetrics() {
        // Post at least one message to ensure messages.processed metric is incremented
        CreateMessageRequest request = new CreateMessageRequest(null, "customer", "Hello, I need help");

        restTestClient.post().uri("/messages")
                .body(request)
                .exchange()
                .expectStatus().isEqualTo(202);

        // GET /actuator/prometheus and verify the response is valid
        String prometheusOutput = restTestClient.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(prometheusOutput).isNotNull();
        // Verify the response contains custom metric family names
        assertThat(prometheusOutput).contains("messages_processed_total");
    }

    @Test
    void prometheusEndpoint_returnsValidPrometheusFormat() {
        // Verify the endpoint is accessible and returns valid Prometheus format
        String prometheusOutput = restTestClient.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(prometheusOutput)
                .isNotNull()
                .isNotEmpty()
                .contains("# HELP")
                .contains("# TYPE");
    }
}
