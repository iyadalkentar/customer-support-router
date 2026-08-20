package com.ikdev.customersupportrouter.aiclassifierservice;

import com.ikdev.customersupportrouter.aiclassifierservice.service.LlmClient;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test that verifies the Prometheus metrics endpoint is exposed
 * and contains custom metrics from ai-classifier-service.
 *
 * Note: llm.provider is overridden to "ollama" to avoid requiring a GEMINI_API_KEY
 * at startup (which the LlmProviderEnvironmentPostProcessor enforces). Kafka listener
 * auto-startup is disabled to avoid needing a live Kafka broker for metrics exposure.
 * The real ChatModel bean is replaced with a Mockito mock (deep-stubbed so the
 * ChatModelService constructor's eager chatModel.getOptions().getModel() call
 * doesn't NPE during context startup) so classify() can be exercised without any
 * real LLM network call, just to make the classification.latency Timer register.
 */
@Testcontainers
@SpringBootTest(
        properties = {
                "llm.provider=ollama",
                "spring.kafka.listener.auto-startup=false"
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureRestTestClient
class PrometheusEndpointIntegrationTest {

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:8.10.0-alpine"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.3.1"));

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private ChatModel chatModel;

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private LlmClient llmClient;

    @Test
    void prometheusEndpoint_isAccessible() {
        // GET /actuator/prometheus and verify the response is valid
        String prometheusOutput = restTestClient.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(prometheusOutput).isNotNull();
        // Verify the endpoint contains standard JVM metrics confirming it's properly configured
        assertThat(prometheusOutput).contains("# HELP jvm_memory_used_bytes");
    }

    @Test
    void prometheusEndpoint_exposesClassificationLatencyMetricFamily() {
        // Trigger one classify() call (mocked ChatModel, no real network) so
        // ClassificationMetrics registers the classification.latency Timer at least once.
        String json = "{\"intent\":\"INFO_REQUEST\",\"sentiment\":\"NEUTRAL\",\"urgency\":\"LOW\"}";
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));

        llmClient.classify("hello", List.of());

        String prometheusOutput = restTestClient.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(prometheusOutput).isNotNull();
        // classification.latency is a Timer; Micrometer's Prometheus exporter appends
        // the base unit (_seconds) to Timer metric family names.
        assertThat(prometheusOutput).contains("classification_latency_seconds");
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
