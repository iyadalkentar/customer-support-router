package com.ikdev.customersupportrouter.aiclassifierservice.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ConversationMemoryReaderTest {

    private static final long CONVERSATION_ID = 7L;
    private static final String KEY = "conversation:7:messages";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ListOperations<String, String> listOps;

    private ObjectMapper objectMapper;
    private ConversationMemoryReader reader;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        reader = new ConversationMemoryReader(redis, objectMapper, new ConversationMemoryProperties(10, true));
    }

    private String entry(long id, String content) {
        return "{\"messageId\":" + id + ",\"conversationId\":7,\"sender\":\"customer\","
                + "\"content\":\"" + content + "\",\"createdAt\":\"2026-08-02T10:00:00Z\"}";
    }

    @Test
    void getRecent_parsesEntriesInOrder() {
        when(redis.opsForList()).thenReturn(listOps);
        when(listOps.range(KEY, 0, 9)).thenReturn(List.of(entry(1L, "first"), entry(2L, "second")));

        List<ConversationContextMessage> result = reader.getRecent(CONVERSATION_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).content()).isEqualTo("first");
        assertThat(result.get(1).content()).isEqualTo("second");
    }

    @Test
    void getRecent_emptyList_returnsEmpty() {
        when(redis.opsForList()).thenReturn(listOps);
        when(listOps.range(KEY, 0, 9)).thenReturn(List.of());

        assertThat(reader.getRecent(CONVERSATION_ID)).isEmpty();
    }

    @Test
    void getRecent_redisThrows_returnsEmptyWithoutPropagating() {
        when(redis.opsForList()).thenReturn(listOps);
        when(listOps.range(KEY, 0, 9)).thenThrow(new RuntimeException("redis down"));

        assertThat(reader.getRecent(CONVERSATION_ID)).isEmpty();
    }

    @Test
    void getRecent_skipsGarbageEntry() {
        when(redis.opsForList()).thenReturn(listOps);
        when(listOps.range(KEY, 0, 9))
                .thenReturn(List.of("not-json", entry(2L, "valid")));

        List<ConversationContextMessage> result = reader.getRecent(CONVERSATION_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("valid");
    }

    @Test
    void getRecent_disabled_doesNotTouchRedis() {
        ConversationMemoryReader disabled = new ConversationMemoryReader(redis, objectMapper,
                new ConversationMemoryProperties(10, false));

        assertThat(disabled.getRecent(CONVERSATION_ID)).isEmpty();
        verifyNoInteractions(redis);
    }
}
