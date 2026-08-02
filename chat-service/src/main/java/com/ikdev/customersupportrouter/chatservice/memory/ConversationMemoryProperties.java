package com.ikdev.customersupportrouter.chatservice.memory;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the chat-service side of conversation memory
 * ({@code conversation.memory.*}). {@code enabled} is a guard inside
 * {@link ConversationMemoryWriter} (not {@code @ConditionalOnProperty}) so the
 * bean always exists and consumers never need {@code required = false}
 * plumbing.
 */
@ConfigurationProperties(prefix = "conversation.memory")
public record ConversationMemoryProperties(int size, Duration ttl, Boolean enabled) {

    public ConversationMemoryProperties {
        if (size <= 0) {
            size = 10;
        }
        if (ttl == null) {
            ttl = Duration.ofHours(24);
        }
        if (enabled == null) {
            enabled = true;
        }
    }
}
