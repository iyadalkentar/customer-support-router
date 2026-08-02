package com.ikdev.customersupportrouter.aiclassifierservice.memory;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test: locks the cross-service wire format. The fixture is a hand-written
 * copy of what chat-service's {@code ConversationMemoryWriter} right-pushes; the
 * reader must parse it in order via the same shared key scheme
 * ({@code conversation:{id}:messages}). Keep this fixture in lockstep with
 * chat-service's {@code ConversationMemoryEntry}.
 */
@Testcontainers
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class ConversationMemoryReaderIntegrationTest {

    @Container
    @ServiceConnection
    static RedisContainer redisContainer =
            new RedisContainer(DockerImageName.parse("redis:8.10.0-alpine"));

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ConversationMemoryReader reader;

    @Test
    void readsFixtureWrittenByChatService_inOrder() {
        redisTemplate.opsForList().rightPushAll("conversation:7:messages",
                "{\"messageId\":1,\"conversationId\":7,\"sender\":\"customer\","
                        + "\"content\":\"I cannot log in\",\"createdAt\":\"2026-08-02T10:00:00Z\"}",
                "{\"messageId\":2,\"conversationId\":7,\"sender\":\"customer\","
                        + "\"content\":\"Account locked now\",\"createdAt\":\"2026-08-02T10:01:00Z\"}");

        assertThat(reader.getRecent(7L))
                .extracting(ConversationContextMessage::content)
                .containsExactly("I cannot log in", "Account locked now");
    }

    @Test
    void missingKey_returnsEmpty() {
        assertThat(reader.getRecent(999L)).isEmpty();
    }
}
