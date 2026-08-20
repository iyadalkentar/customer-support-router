package com.ikdev.customersupportrouter.chatservice;

import com.ikdev.customersupportrouter.chatservice.dto.CreateMessageRequest;
import com.ikdev.customersupportrouter.chatservice.dto.MessageResponse;
import com.ikdev.customersupportrouter.chatservice.event.MessageEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer; // new package
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class MessageFlowIntegrationTest {

    private static final String TOPIC = "incoming-messages";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    // @ServiceConnection auto-wires spring.kafka.bootstrap-servers to the
    // container's mapped port, so no manual @DynamicPropertySource is needed.
    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    @Autowired
    private RestTestClient restTestClient;

    private Consumer<String, MessageEvent> testConsumer;

    @BeforeEach
    void setUpTestConsumer() {
        String groupId = "test-" + UUID.randomUUID();

        Map<String, Object> props = KafkaTestUtils.consumerProps(
                kafka.getBootstrapServers(), groupId, true);

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES,
                "com.ikdev.customersupportrouter.chatservice.event");
        props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, MessageEvent.class.getName());
        props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        testConsumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(props);
        testConsumer.subscribe(List.of(TOPIC));

        // Join the consumer group before the application publishes anything.
        testConsumer.poll(Duration.ofMillis(500));
    }

    @AfterEach
    void tearDownTestConsumer() {
        if (testConsumer != null) {
            testConsumer.close();
        }
    }

    @Test
    void postingMessageWithoutConversationId_createsNewConversationAndPersistsMessage() {
        CreateMessageRequest request = new CreateMessageRequest(null, "customer", "Hello, I need help");

        restTestClient.post().uri("/messages")
                .body(request)
                .exchange()
                .expectStatus().isEqualTo(202)
                .expectBody(MessageResponse.class)
                .value(body -> {
                    assert body.id() != null;
                    assert body.createdAt() != null;
                    assert body.conversationId() != null;
                });
    }

    @Test
    void postingMessage_publishesMessageEventToKafka_afterCommit() {
        // This is the test that actually proves the AFTER_COMMIT contract:
        // it runs the full HTTP request through a real transaction manager
        // (no @Transactional on the test itself, so nothing auto-rolls-back)
        // and a real Kafka broker, then polls for the event on the consumer
        // side rather than asserting immediately — the publish happens on a
        // separate thread from the HTTP response.
        CreateMessageRequest request = new CreateMessageRequest(null, "agent", "Following up on your ticket");

        MessageResponse response = restTestClient.post().uri("/messages")
                .body(request)
                .exchange()
                .expectStatus().isEqualTo(202)
                .expectBody(MessageResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {

            ConsumerRecords<String, MessageEvent> records =
                    testConsumer.poll(Duration.ofSeconds(2));

            ConsumerRecord<String, MessageEvent> record =
                    StreamSupport.stream(records.spliterator(), false)
                            .filter(r -> r.value().messageId().equals(response.id()))
                            .findFirst()
                            .orElseThrow(() ->
                                    new AssertionError("Expected Kafka event not received"));

            assertThat(record.key())
                    .isEqualTo(String.valueOf(response.conversationId()));

            MessageEvent event = record.value();

            assertThat(event.messageId()).isEqualTo(response.id());
            assertThat(event.conversationId()).isEqualTo(response.conversationId());
            assertThat(event.sender()).isEqualTo("agent");
            assertThat(event.content()).isEqualTo("Following up on your ticket");
            assertThat(event.createdAt()).isEqualTo(response.createdAt());
        });
    }

    @Test
    void gettingMessagesForUnknownConversation_returns404() {
        restTestClient.get().uri("/conversations/999999/messages")
                .exchange()
                .expectStatus().isEqualTo(404);
    }
}