package com.ikdev.customersupportrouter.chatservice;

import java.util.List;

import com.ikdev.customersupportrouter.chatservice.dto.CreateMessageRequest;
import com.ikdev.customersupportrouter.chatservice.dto.MessageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resilience: with NO Redis available (port pinned to 1 so every connection is
 * refused), message ingest must still succeed and persist to Postgres — the
 * writer's AFTER_COMMIT callback catches the Redis failure and logs it instead
 * of propagating.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.data.redis.port=1")
@AutoConfigureRestTestClient
class ConversationMemoryRedisDownIntegrationTest {

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

    @Test
    void postingMessage_succeedsWhenRedisIsDown() {
        MessageResponse post = restTestClient.post().uri("/messages")
                .body(new CreateMessageRequest(null, "customer", "Hello even without Redis"))
                .exchange()
                .expectStatus().isEqualTo(202)
                .expectBody(MessageResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(post).isNotNull();

        // Read-back proves the message reached Postgres despite Redis being down.
        List<MessageResponse> messages = restTestClient.get()
                .uri("/conversations/{id}/messages", post.conversationId())
                .exchange()
                .expectStatus().isEqualTo(200)
                .expectBody(new ParameterizedTypeReference<List<MessageResponse>>() {})
                .returnResult()
                .getResponseBody();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).content()).isEqualTo("Hello even without Redis");
    }
}
