package com.ikdev.customersupportrouter.chatservice;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ikdev.customersupportrouter.chatservice.dto.CreateMessageRequest;
import com.ikdev.customersupportrouter.chatservice.dto.MessageResponse;
import com.ikdev.customersupportrouter.chatservice.entity.Message;
import com.ikdev.customersupportrouter.chatservice.event.ClassificationResult;
import com.ikdev.customersupportrouter.chatservice.repository.MessageRepository;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end test for the closing leg: a {@link ClassificationResult} published on
 * the {@code classification-results} topic — the same way ai-classifier-service
 * publishes it — must land on its corresponding {@link Message} row, and the
 * classification fields must surface in the REST read-back (per the
 * MessageResponse DTO exposure).
 *
 * <p>The producer side mimics production: a raw {@code KafkaProducer} with
 * {@link JacksonJsonSerializer} and no type headers — exactly as the production
 * publisher uses. The consumer side is the real {@code @KafkaListener} on the
 * real Spring context, so this proves the wiring, not just the service method.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ClassificationClosingLegIntegrationTest {

    private static final String TOPIC = "classification-results";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.3.1"));

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private MessageRepository messageRepository;

    private Producer<String, ClassificationResult> testProducer;

    @BeforeEach
    void setUpTestProducer() {
        Map<String, Object> props = new HashMap<>(
                KafkaTestUtils.producerProps(kafka.getBootstrapServers()));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Match the production publisher side: JacksonJsonSerializer without type
        // info headers. The consumer relies on spring.json.value.default.type to
        // land the record on ClassificationResult.
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        testProducer = new KafkaProducer<>(props);
    }

    @AfterEach
    void tearDownTestProducer() {
        if (testProducer != null) {
            testProducer.close();
        }
    }

    @Test
    void publishedClassificationResult_persistsOnMessage_andIsExposedViaReadBack()
            throws Exception {
        // Arrange: post a message via REST so chat-service writes the row.
        CreateMessageRequest request = new CreateMessageRequest(null, "customer",
                "Hello, I need help");
        MessageResponse post = restTestClient.post().uri("/messages")
                .body(request)
                .exchange()
                .expectStatus().isEqualTo(202)
                .expectBody(MessageResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(post).isNotNull();

        // Act: publish a classification result on the topic (mimics ai-classifier-service).
        ClassificationResult result = new ClassificationResult(
                post.id(), post.conversationId(),
                "REQUEST_REFUND", "NEUTRAL", "MEDIUM",
                UUID.randomUUID(), 1);
        testProducer
                .send(new ProducerRecord<>(TOPIC, String.valueOf(post.conversationId()), result))
                .get(5, SECONDS);

        // Assert: row gets classification populated, via the real @KafkaListener path.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Message persisted = messageRepository.findById(post.id()).orElseThrow();
            assertThat(persisted.getIntent()).isEqualTo("REQUEST_REFUND");
            assertThat(persisted.getSentiment()).isEqualTo("NEUTRAL");
            assertThat(persisted.getUrgency()).isEqualTo("MEDIUM");
        });

        // Assert: the read-back API exposes the classification (task #4 guarantee).
        List<MessageResponse> messages = restTestClient.get()
                .uri("/conversations/{id}/messages", post.conversationId())
                .exchange()
                .expectStatus().isEqualTo(200)
                .expectBody(new ParameterizedTypeReference<List<MessageResponse>>() {})
                .returnResult()
                .getResponseBody();

        MessageResponse classified = messages.stream()
                .filter(m -> m.id().equals(post.id()))
                .findFirst()
                .orElseThrow();
        assertThat(classified.intent()).isEqualTo("REQUEST_REFUND");
        assertThat(classified.sentiment()).isEqualTo("NEUTRAL");
        assertThat(classified.urgency()).isEqualTo("MEDIUM");
    }

    @Test
    void publishedClassificationResult_isIdempotentOnRedelivery() throws Exception {
        MessageResponse post = restTestClient.post().uri("/messages")
                .body(new CreateMessageRequest(null, "agent", "Following up"))
                .exchange()
                .expectStatus().isEqualTo(202)
                .expectBody(MessageResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(post).isNotNull();

        ClassificationResult first = new ClassificationResult(
                post.id(), post.conversationId(),
                "INFO_REQUEST", "POSITIVE", "LOW",
                UUID.randomUUID(), 1);

        // Send the same result twice in a row — the second must be a no-op,
        // not a duplicate-write failure.
        testProducer.send(new ProducerRecord<>(TOPIC, String.valueOf(post.conversationId()), first))
                .get(5, SECONDS);
        testProducer.send(new ProducerRecord<>(TOPIC, String.valueOf(post.conversationId()), first))
                .get(5, SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Message persisted = messageRepository.findById(post.id()).orElseThrow();
            assertThat(persisted.getIntent()).isEqualTo("INFO_REQUEST");
            assertThat(persisted.getSentiment()).isEqualTo("POSITIVE");
        });

        // Then send a *different* result and verify it overwrites — proving both
        // earlier deliveries left the consumer in a healthy state.
        ClassificationResult updated = new ClassificationResult(
                post.id(), post.conversationId(),
                "COMPLAINT", "NEGATIVE", "HIGH",
                UUID.randomUUID(), 1);
        testProducer
                .send(new ProducerRecord<>(TOPIC, String.valueOf(post.conversationId()), updated))
                .get(5, SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Message persisted = messageRepository.findById(post.id()).orElseThrow();
            assertThat(persisted.getIntent()).isEqualTo("COMPLAINT");
            assertThat(persisted.getSentiment()).isEqualTo("NEGATIVE");
            assertThat(persisted.getUrgency()).isEqualTo("HIGH");
        });
    }

    @Test
    void classificationResult_forUnknownMessageId_isDroppedGracefully() throws Exception {
        long bogusMessageId = 999_999_999L;
        long bogusConversationId = 999_999_998L;

        ClassificationResult bogus = new ClassificationResult(
                bogusMessageId, bogusConversationId,
                "X", "Y", "Z", UUID.randomUUID(), 1);
        testProducer
                .send(new ProducerRecord<>(TOPIC, String.valueOf(bogusConversationId), bogus))
                .get(5, SECONDS);

        // The real proof that the listener didn't wedge on the bogus message is that a
        // follow-up legitimate publish still gets applied to its row.
        MessageResponse post = restTestClient.post().uri("/messages")
                .body(new CreateMessageRequest(null, "agent", "Post-bogus sanity check"))
                .exchange()
                .expectStatus().isEqualTo(202)
                .expectBody(MessageResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(post).isNotNull();

        ClassificationResult healthy = new ClassificationResult(
                post.id(), post.conversationId(),
                "INFO_REQUEST", "NEUTRAL", "LOW",
                UUID.randomUUID(), 1);
        testProducer
                .send(new ProducerRecord<>(TOPIC, String.valueOf(post.conversationId()), healthy))
                .get(5, SECONDS);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Message persisted = messageRepository.findById(post.id()).orElseThrow();
            assertThat(persisted.getIntent()).isEqualTo("INFO_REQUEST");
        });

        // Defensive: confirm the bogus id never materialized.
        assertThat(messageRepository.findById(bogusMessageId)).isEmpty();
    }
}
