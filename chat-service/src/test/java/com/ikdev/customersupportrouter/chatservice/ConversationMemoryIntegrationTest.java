package com.ikdev.customersupportrouter.chatservice;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.ikdev.customersupportrouter.chatservice.dto.CreateMessageRequest;
import com.ikdev.customersupportrouter.chatservice.dto.MessageResponse;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Proves the write-through cache: messages POSTed via the REST API land in the
 * per-conversation Redis list (last N, oldest first), with a TTL set. The
 * {@code AFTER_COMMIT} Redis write runs asynchronously on the single-threaded
 * {@code conversationMemoryExecutor} (see {@code AsyncConfig}), so the assertions
 * below {@code await()} the list rather than reading it synchronously after the 202.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "conversation.memory.size=3")
@AutoConfigureRestTestClient
class ConversationMemoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Container
    @ServiceConnection
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.3.1"));

    @Container
    @ServiceConnection
    static RedisContainer redisContainer =
            new RedisContainer(DockerImageName.parse("redis:8.10.0-alpine"));

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private StringRedisTemplate redis;

    private MessageResponse post(String sender, String content, Long conversationId) {
        return restTestClient.post().uri("/messages")
                .body(new CreateMessageRequest(conversationId, sender, content))
                .exchange()
                .expectStatus().isEqualTo(202)
                .expectBody(MessageResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private List<String> list(Long conversationId) {
        return redis.opsForList().range(key(conversationId), 0, -1);
    }

    private String key(Long conversationId) {
        return "conversation:" + conversationId + ":messages";
    }

    @Test
    void postingFirstMessage_writesSingleEntryAndSetsTtl() {
        MessageResponse first = post("customer", "Hello", null);
        assertThat(first).isNotNull();

        // The AFTER_COMMIT Redis write is async (@Async conversationMemoryExecutor), so wait
        // for it rather than asserting synchronously after the 202 returns.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<String> entries = list(first.conversationId());
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0))
                    .contains("\"messageId\":" + first.id(), "\"conversationId\":" + first.conversationId(),
                            "\"sender\":\"customer\"", "\"content\":\"Hello\"");
            assertThat(redis.getExpire(key(first.conversationId()), TimeUnit.SECONDS)).isGreaterThan(0L);
        });
    }

    @Test
    void postingSecondMessage_appendsNewestLast() {
        MessageResponse first = post("customer", "Hello", null);
        MessageResponse second = post("customer", "Follow up", first.conversationId());

        // Single-threaded async writer preserves append order; wait for the second write.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<String> entries = list(first.conversationId());
            assertThat(entries).hasSize(2);
            assertThat(entries.get(0)).contains("\"messageId\":" + first.id());
            assertThat(entries.get(1)).contains("\"messageId\":" + second.id());
        });
    }

    @Test
    void postingMoreThanSizeMessages_trimsToLastN() {
        MessageResponse first = post("customer", "m1", null);
        MessageResponse second = post("customer", "m2", first.conversationId());
        MessageResponse third = post("customer", "m3", first.conversationId());
        MessageResponse fourth = post("customer", "m4", first.conversationId());
        MessageResponse fifth = post("customer", "m5", first.conversationId());

        // conversation.memory.size=3 → only the last 3 remain, oldest first.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<String> entries = list(first.conversationId());
            assertThat(entries).hasSize(3);
            assertThat(entries.get(0)).contains("\"messageId\":" + third.id());
            assertThat(entries.get(1)).contains("\"messageId\":" + fourth.id());
            assertThat(entries.get(2)).contains("\"messageId\":" + fifth.id());
        });
    }
}
