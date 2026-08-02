package com.ikdev.customersupportrouter.aiclassifierservice.memory;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Reads recent conversation turns from Redis (written by chat-service's
 * {@code ConversationMemoryWriter}). Returns oldest→newest (the writer
 * right-pushes, so list left = oldest). Unparseable entries are skipped.
 *
 * <p>Any Redis failure degrades gracefully to an empty list — classification
 * proceeds without context rather than failing.
 *
 * <p>Key scheme: {@code conversation:{conversationId}:messages} — MUST stay in
 * sync with chat-service's {@code ConversationMemoryWriter}.
 */
@Component
public class ConversationMemoryReader {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryReader.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ConversationMemoryProperties properties;

    public ConversationMemoryReader(StringRedisTemplate redis, ObjectMapper objectMapper,
            ConversationMemoryProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<ConversationContextMessage> getRecent(Long conversationId) {
        if (!properties.enabled()) {
            return List.of();
        }
        String key = key(conversationId);
        try {
            List<String> raw = redis.opsForList().range(key, 0, Math.max(0, properties.size() - 1));
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            return raw.stream().map(this::parse).filter(Objects::nonNull).toList();
        } catch (Exception ex) {
            log.warn("Failed to read conversation memory for conversationId={}; classifying without context",
                    conversationId, ex);
            return List.of();
        }
    }

    private ConversationContextMessage parse(String json) {
        try {
            return objectMapper.readValue(json, ConversationContextMessage.class);
        } catch (Exception ex) {
            log.debug("Skipping unparseable context entry: {}", json);
            return null;
        }
    }

    static String key(Long conversationId) {
        return "conversation:" + conversationId + ":messages";
    }
}
