package com.ikdev.customersupportrouter.chatservice.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.ikdev.customersupportrouter.chatservice.entity.Message;
import com.ikdev.customersupportrouter.chatservice.event.MessageEvent;
import com.ikdev.customersupportrouter.chatservice.event.MessagePersistedEvent;
import com.ikdev.customersupportrouter.chatservice.repository.MessageRepository;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ConversationMemoryWriterTest {

    private static final long CONVERSATION_ID = 7L;
    private static final String KEY = "conversation:7:messages";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ListOperations<String, String> listOps;
    @Mock
    private MessageRepository messageRepository;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        // The writer pipelines append (rightPush/expire/trim) via executePipelined(RedisCallback).
        // A mocked template would normally swallow the callback, so run it here against the mocks
        // so the inner ops are observable/verifiable. lenient(): not every test hits the append path.
        lenient().when(redis.executePipelined(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<Object> callback = invocation.getArgument(0);
            callback.doInRedis(null);
            return List.of();
        });
    }

    private ConversationMemoryWriter writer(ConversationMemoryProperties properties) {
        return new ConversationMemoryWriter(redis, messageRepository, objectMapper, properties);
    }

    private MessageEvent event(long messageId, String content) {
        return new MessageEvent(messageId, CONVERSATION_ID, "customer", content,
                OffsetDateTime.parse("2026-08-02T10:00:00Z"), 1, UUID.randomUUID());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private Message message(long id, String content) {
        Message message = new Message();
        message.setId(id);
        message.setSender("customer");
        message.setContent(content);
        message.setCreatedAt(OffsetDateTime.parse("2026-08-02T10:00:00Z"));
        return message;
    }

    @Test
    void onMessagePersisted_keyPresent_appendsTrimsAndRefreshesTtl() {
        ConversationMemoryWriter writer = writer(new ConversationMemoryProperties(10, Duration.ofHours(24), true));
        MessageEvent event = event(42L, "hello");

        when(redis.opsForList()).thenReturn(listOps);
        when(redis.hasKey(KEY)).thenReturn(true);

        writer.onMessagePersisted(new MessagePersistedEvent(event));

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOps).rightPush(eq(KEY), jsonCaptor.capture());
        assertThat(jsonCaptor.getValue())
                .contains("\"messageId\":42", "\"conversationId\":7",
                        "\"sender\":\"customer\"", "\"content\":\"hello\"");
        verify(redis).expire(KEY, Duration.ofHours(24));
        verify(listOps).trim(KEY, -10, -1);
    }

    @Test
    void onMessagePersisted_keyMissing_seedsPriorThenAppendsCurrent() {
        ConversationMemoryWriter writer = writer(new ConversationMemoryProperties(2, Duration.ofHours(24), true));
        MessageEvent event = event(42L, "hello");

        when(redis.opsForList()).thenReturn(listOps);
        when(redis.hasKey(KEY)).thenReturn(false);
        when(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(CONVERSATION_ID))
                .thenReturn(List.of(message(40L, "oldest"), message(41L, "middle"), message(42L, "hello")));

        writer.onMessagePersisted(new MessagePersistedEvent(event));

        // size=2 → the seed window is size-1 = 1, strictly older than the current message:
        // the seed contains only message 41; message 42 is appended separately, so it lands
        // in the list exactly once.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> seedCaptor = ArgumentCaptor.forClass(List.class);
        verify(listOps).rightPushAll(eq(KEY), seedCaptor.capture());
        assertThat(seedCaptor.getValue()).hasSize(1);
        assertThat(seedCaptor.getValue().get(0)).contains("\"messageId\":41");

        ArgumentCaptor<String> appendCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOps).rightPush(eq(KEY), appendCaptor.capture());
        assertThat(appendCaptor.getValue()).contains("\"messageId\":42");

        verify(redis).expire(KEY, Duration.ofHours(24));
        verify(listOps).trim(KEY, -2, -1);
    }

    @Test
    void concurrentFirstPosts_backfillRunsOnceAndBothMessagesAppend() throws Exception {
        ConversationMemoryWriter writer = writer(new ConversationMemoryProperties(10, Duration.ofHours(24), true));

        AtomicBoolean seeded = new AtomicBoolean(false);
        AtomicInteger backfillCount = new AtomicInteger();
        CopyOnWriteArrayList<String> pushed = new CopyOnWriteArrayList<>();

        when(redis.opsForList()).thenReturn(listOps);
        // Stateful hasKey: false until the first backfill seeds the key.
        when(redis.hasKey(KEY)).thenAnswer(inv -> seeded.get());
        when(listOps.rightPushAll(eq(KEY), anyList())).thenAnswer(inv -> {
            seeded.set(true);
            backfillCount.incrementAndGet();
            return 1L;
        });
        when(listOps.rightPush(eq(KEY), anyString())).thenAnswer(inv -> {
            pushed.add(inv.getArgument(1));
            return 1L;
        });
        when(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(CONVERSATION_ID))
                .thenReturn(List.of(message(40L, "oldest"), message(41L, "middle")));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> f1 = pool.submit(() -> {
            await(start);
            writer.onMessagePersisted(new MessagePersistedEvent(event(42L, "hello")));
        });
        Future<?> f2 = pool.submit(() -> {
            await(start);
            writer.onMessagePersisted(new MessagePersistedEvent(event(43L, "follow up")));
        });
        start.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        // The double-backfill race is gone: exactly one thread seeds the list.
        assertThat(backfillCount.get()).isEqualTo(1);
        // Both current messages appended exactly once each.
        assertThat(pushed).hasSize(2);
        assertThat(pushed).anySatisfy(s -> assertThat(s).contains("\"messageId\":42"));
        assertThat(pushed).anySatisfy(s -> assertThat(s).contains("\"messageId\":43"));
    }

    @Test
    void onMessagePersisted_redisThrows_doesNotPropagate() {
        ConversationMemoryWriter writer = writer(new ConversationMemoryProperties(10, Duration.ofHours(24), true));
        when(redis.hasKey(KEY)).thenThrow(new RuntimeException("redis down"));

        assertThatNoException().isThrownBy(() ->
                writer.onMessagePersisted(new MessagePersistedEvent(event(42L, "hello"))));
    }

    @Test
    void onMessagePersisted_disabled_doesNothing() {
        ConversationMemoryWriter writer = writer(new ConversationMemoryProperties(10, Duration.ofHours(24), false));

        writer.onMessagePersisted(new MessagePersistedEvent(event(42L, "hello")));

        verifyNoInteractions(redis, messageRepository);
    }
}
