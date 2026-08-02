package com.ikdev.customersupportrouter.chatservice.memory;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ikdev.customersupportrouter.chatservice.entity.Message;
import com.ikdev.customersupportrouter.chatservice.event.MessageEvent;
import com.ikdev.customersupportrouter.chatservice.event.MessagePersistedEvent;
import com.ikdev.customersupportrouter.chatservice.repository.MessageRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Write-through conversation memory: after a message's DB transaction commits,
 * appends it to the per-conversation Redis list, trimming to the last N messages
 * and refreshing the TTL.
 *
 * <p>Redis is a fast-access layer; Postgres is the source of truth, so a Redis
 * failure is caught and logged here and never propagates out of the AFTER_COMMIT
 * callback (which would otherwise surface to the HTTP caller).
 *
 * <p>Key scheme: {@code conversation:{conversationId}:messages} — MUST stay in
 * sync with ai-classifier-service's {@code ConversationMemoryReader}.
 *
 * <p>On a cache miss the key is seeded from Postgres with strictly-prior messages
 * (ids older than the current one) and the current message is always appended
 * once — so a backfill can never capture a message whose own callback would later
 * append it again. Backfills are serialized per conversation to keep concurrent
 * first-posts to a fresh conversation from seeding the list twice.
 */
@Component
public class ConversationMemoryWriter {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryWriter.class);

    private final StringRedisTemplate redis;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final ConversationMemoryProperties properties;
    // Serializes the backfill (miss) path per conversation. In production the single-threaded
    // conversationMemoryExecutor already serializes all writer work, so this lock's real job is
    // (a) the concurrentFirstPosts unit test, which invokes the writer directly without the
    // executor, and (b) defense-in-depth if the executor is ever widened to >1 thread — two
    // concurrent misses would otherwise backfill the same Postgres rows and duplicate the list.
    // A stripe is fine: the critical section is tiny. In-JVM only; a distributed lock would be
    // needed for multi-instance (see phase-5-status-note.md).
    private final Object[] backfillLocks;

    public ConversationMemoryWriter(StringRedisTemplate redis, MessageRepository messageRepository,
            ObjectMapper objectMapper, ConversationMemoryProperties properties) {
        this.redis = redis;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.backfillLocks = new Object[16];
        for (int i = 0; i < backfillLocks.length; i++) {
            backfillLocks[i] = new Object();
        }
    }

    // Runs off the request thread (see AsyncConfig): the AFTER_COMMIT trigger fires on the
    // HTTP thread but the body executes on the single-threaded conversationMemoryExecutor,
    // so a slow Redis never blocks the POST response. The try/catch below still swallows
    // Redis failures; they surface only in the logs.
    @Async("conversationMemoryExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessagePersisted(MessagePersistedEvent persistedEvent) {
        if (!properties.enabled()) {
            return;
        }
        MessageEvent event = persistedEvent.getMessageEvent();
        try {
            String key = key(event.conversationId());
            if (Boolean.TRUE.equals(redis.hasKey(key))) {
                append(event, key);
            } else {
                backfillAndAppend(event, key);
            }
            log.debug("Conversation memory updated: conversationId={}, messageId={}",
                    event.conversationId(), event.messageId());
        } catch (Exception ex) {
            // Never throw out of AFTER_COMMIT: the transaction is already committed and an
            // exception here propagates to the HTTP caller. Postgres is the source of truth;
            // a Redis failure is a cache miss, not an ingest failure.
            log.error("Failed to write conversation memory — Postgres remains source of truth. "
                    + "messageId={}, conversationId={}", event.messageId(), event.conversationId(), ex);
        }
    }

    private void append(MessageEvent event, String key) {
        String json = toJson(ConversationMemoryEntry.from(event));
        // Pipeline rightPush/expire/trim into ONE Redis round trip on the hot path (three
        // sequential ops would be three round trips on the request thread). executePipelined
        // binds the template connection in pipeline mode for the callback, so the template ops
        // below are queued and flushed once at the end.
        redis.executePipelined((RedisCallback<Object>) connection -> {
            redis.opsForList().rightPush(key, json);
            redis.expire(key, properties.ttl());
            redis.opsForList().trim(key, -properties.size(), -1);
            return null;
        });
    }

    private void backfillAndAppend(MessageEvent event, String key) {
        synchronized (lockFor(event.conversationId())) {
            // Serialized per conversation: two concurrent first-posts must not both seed the
            // list (the double-backfill race). The thread that loses the lock finds the key
            // already created here and only appends its own message below.
            if (Boolean.FALSE.equals(redis.hasKey(key))) {
                seedPriorMessages(event, key);
            }
            // The current message is appended unconditionally: seedPriorMessages() only ever
            // contains strictly-older ids, so the current message lands in the list exactly
            // once regardless of which thread won the lock.
            append(event, key);
        }
    }

    private void seedPriorMessages(MessageEvent event, String key) {
        List<Message> all = messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(event.conversationId());
        List<Message> prior = all.stream()
                .filter(m -> m.getId() < event.messageId())
                .toList();
        // Keep room for the current append so the list never exceeds size before the trim.
        int from = Math.max(0, prior.size() - (properties.size() - 1));
        List<String> jsons = prior.subList(from, prior.size()).stream()
                .map(m -> toJson(new ConversationMemoryEntry(
                        m.getId(), event.conversationId(), m.getSender(), m.getContent(), m.getCreatedAt())))
                .toList();
        if (!jsons.isEmpty()) {
            redis.opsForList().rightPushAll(key, jsons);
        }
    }

    private Object lockFor(Long conversationId) {
        int idx = (conversationId.hashCode() & 0x7fffffff) % backfillLocks.length;
        return backfillLocks[idx];
    }

    private String toJson(ConversationMemoryEntry entry) {
        return objectMapper.writeValueAsString(entry);
    }

    static String key(Long conversationId) {
        return "conversation:" + conversationId + ":messages";
    }
}
