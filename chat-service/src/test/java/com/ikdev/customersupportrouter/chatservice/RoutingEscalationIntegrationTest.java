package com.ikdev.customersupportrouter.chatservice;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ikdev.customersupportrouter.chatservice.dto.CreateMessageRequest;
import com.ikdev.customersupportrouter.chatservice.dto.MessageResponse;
import com.ikdev.customersupportrouter.chatservice.entity.Message;
import com.ikdev.customersupportrouter.chatservice.entity.RoutingDecision;
import com.ikdev.customersupportrouter.chatservice.entity.TicketStatus;
import com.ikdev.customersupportrouter.chatservice.event.ClassificationResult;
import com.ikdev.customersupportrouter.chatservice.event.EscalationEvent;
import com.ikdev.customersupportrouter.chatservice.repository.MessageRepository;
import com.ikdev.customersupportrouter.chatservice.repository.TicketRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
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
 * End-to-end test for Phase 4 routing/escalation: a {@link ClassificationResult}
 * published on {@code classification-results} (as ai-classifier-service does)
 * must be routed by the real {@code @KafkaListener} — recording
 * {@code routing_decision}, creating/reusing an OPEN ticket on escalation, and
 * publishing an {@link EscalationEvent} on {@code escalations} keyed by
 * conversationId.
 *
 * <p>Producer side mimics production (raw {@code KafkaProducer} with
 * {@link JacksonJsonSerializer}, no type headers). Consumer side is the real
 * Spring context. The {@code escalations} side is asserted with a raw test
 * consumer configured the same way as {@link MessageFlowIntegrationTest}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class RoutingEscalationIntegrationTest {

    private static final String CLASSIFICATION_TOPIC = "classification-results";
    private static final String ESCALATIONS_TOPIC = "escalations";

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

    @Autowired
    private TicketRepository ticketRepository;

    private Producer<String, ClassificationResult> testProducer;
    private Consumer<String, EscalationEvent> testConsumer;
    private final List<ConsumerRecord<String, EscalationEvent>> receivedEscalations = new ArrayList<>();

    @BeforeEach
    void setUpKafkaClients() {
        Map<String, Object> producerProps = new HashMap<>(
                KafkaTestUtils.producerProps(kafka.getBootstrapServers()));
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Match the production publisher side: JacksonJsonSerializer without type
        // info headers; the consumer relies on spring.json.value.default.type.
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        testProducer = new KafkaProducer<>(producerProps);

        String groupId = "test-" + UUID.randomUUID();
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                kafka.getBootstrapServers(), groupId, true);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        consumerProps.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);
        consumerProps.put(JacksonJsonDeserializer.TRUSTED_PACKAGES,
                "com.ikdev.customersupportrouter.chatservice.event");
        consumerProps.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, EscalationEvent.class.getName());
        consumerProps.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        testConsumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(consumerProps);
        testConsumer.subscribe(List.of(ESCALATIONS_TOPIC));

        // Join the consumer group before anything is published.
        testConsumer.poll(Duration.ofMillis(500));
    }

    @AfterEach
    void tearDownKafkaClients() {
        if (testProducer != null) {
            testProducer.close();
        }
        if (testConsumer != null) {
            testConsumer.close();
        }
    }

    @Test
    void negativeHigh_createsTicket_publishesEscalationEvent_exposesRoutingDecision()
            throws Exception {
        MessageResponse post = postMessage();
        publish(post, "COMPLAINT", "NEGATIVE", "HIGH");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Message persisted = messageRepository.findById(post.id()).orElseThrow();
            assertThat(persisted.getIntent()).isEqualTo("COMPLAINT");
            assertThat(persisted.getRoutingDecision()).isEqualTo(RoutingDecision.ESCALATE_TO_HUMAN);
        });

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(ticketRepository.findByConversationId(post.conversationId()))
                        .singleElement()
                        .satisfies(t -> assertThat(t.getStatus()).isEqualTo(TicketStatus.OPEN)));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            pollEscalations(Duration.ofSeconds(1));
            assertThat(receivedEscalations).anySatisfy(r -> {
                assertThat(r.key()).isEqualTo(String.valueOf(post.conversationId()));
                assertThat(r.value().messageId()).isEqualTo(post.id());
                assertThat(r.value().conversationId()).isEqualTo(post.conversationId());
                assertThat(r.value().routingDecision()).isEqualTo(RoutingDecision.ESCALATE_TO_HUMAN);
            });
        });

        restTestClient.get().uri("/conversations/{id}/messages", post.conversationId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<MessageResponse>>() {})
                .value(messages -> assertThat(messages).anySatisfy(m -> {
                    assertThat(m.id()).isEqualTo(post.id());
                    assertThat(m.routingDecision()).isEqualTo("ESCALATE_TO_HUMAN");
                }));
    }

    @Test
    void redelivery_sameResult_doesNotDuplicateTicketOrEscalationEvent()
            throws Exception {
        MessageResponse post = postMessage();
        ClassificationResult result = classification(post, "COMPLAINT", "NEGATIVE", "HIGH");
        // A fresh ProducerRecord per send: the serializer closes a record's
        // headers after the first send, so a reused record throws.
        testProducer.send(new ProducerRecord<>(CLASSIFICATION_TOPIC,
                String.valueOf(post.conversationId()), result)).get(5, SECONDS);
        testProducer.send(new ProducerRecord<>(CLASSIFICATION_TOPIC,
                String.valueOf(post.conversationId()), result)).get(5, SECONDS); // at-least-once redelivery

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(ticketRepository.findByConversationId(post.conversationId())).hasSize(1));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            pollEscalations(Duration.ofSeconds(1));
            assertThat(escalationsFor(post.id())).hasSize(1);
        });

        // Settle window: no second event for the same message may arrive.
        pollEscalations(Duration.ofSeconds(3));
        assertThat(escalationsFor(post.id())).hasSize(1);
    }

    @Test
    void autoRespond_recordsDecision_withoutTicketOrEscalationEvent() throws Exception {
        MessageResponse post = postMessage();
        publish(post, "INFO_REQUEST", "NEUTRAL", "LOW");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Message persisted = messageRepository.findById(post.id()).orElseThrow();
            assertThat(persisted.getRoutingDecision()).isEqualTo(RoutingDecision.AUTO_RESPOND);
        });

        assertThat(ticketRepository.findByConversationId(post.conversationId())).isEmpty();

        pollEscalations(Duration.ofSeconds(3));
        assertThat(escalationsFor(post.id())).isEmpty();
    }

    @Test
    void llmFallback_unknownNeutralUnknown_routesToAutoRespond() throws Exception {
        MessageResponse post = postMessage();
        publish(post, "UNKNOWN", "NEUTRAL", "UNKNOWN");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Message persisted = messageRepository.findById(post.id()).orElseThrow();
            assertThat(persisted.getRoutingDecision()).isEqualTo(RoutingDecision.AUTO_RESPOND);
        });

        assertThat(ticketRepository.findByConversationId(post.conversationId())).isEmpty();
        pollEscalations(Duration.ofSeconds(3));
        assertThat(escalationsFor(post.id())).isEmpty();
    }

    @Test
    void deescalation_correctedClassification_closesOpenTicket() throws Exception {
        MessageResponse post = postMessage();
        publish(post, "COMPLAINT", "NEGATIVE", "HIGH"); // escalates -> OPEN ticket

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(ticketRepository.findByConversationId(post.conversationId()))
                        .singleElement()
                        .satisfies(t -> assertThat(t.getStatus()).isEqualTo(TicketStatus.OPEN)));

        publish(post, "INFO_REQUEST", "NEUTRAL", "LOW"); // corrected -> AUTO_RESPOND

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(ticketRepository.findByConversationId(post.conversationId()))
                        .singleElement()
                        .satisfies(t -> assertThat(t.getStatus()).isEqualTo(TicketStatus.CLOSED)));
    }

    // --- helpers ---

    private MessageResponse postMessage() {
        CreateMessageRequest request = new CreateMessageRequest(null, "customer",
                "Hello, I need help");
        MessageResponse response = restTestClient.post().uri("/messages")
                .body(request)
                .exchange()
                .expectStatus().isEqualTo(202)
                .expectBody(MessageResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(response).isNotNull();
        return response;
    }

    private ClassificationResult classification(MessageResponse post, String intent,
            String sentiment, String urgency) {
        return new ClassificationResult(post.id(), post.conversationId(),
                intent, sentiment, urgency, UUID.randomUUID(), 1);
    }

    private void publish(MessageResponse post, String intent, String sentiment, String urgency)
            throws Exception {
        testProducer
                .send(new ProducerRecord<>(CLASSIFICATION_TOPIC,
                        String.valueOf(post.conversationId()),
                        classification(post, intent, sentiment, urgency)))
                .get(5, SECONDS);
    }

    private void pollEscalations(Duration pollDuration) {
        ConsumerRecords<String, EscalationEvent> records = testConsumer.poll(pollDuration);
        records.forEach(receivedEscalations::add);
    }

    private List<ConsumerRecord<String, EscalationEvent>> escalationsFor(Long messageId) {
        return receivedEscalations.stream()
                .filter(r -> r.value().messageId().equals(messageId))
                .toList();
    }
}
