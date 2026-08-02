package com.ikdev.customersupportrouter.aiclassifierservice.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the ai-classifier-service side of conversation memory
 * ({@code conversation.memory.*}). Reader needs only {@code size} (a defensive
 * LRANGE cap) and {@code enabled} (graceful-degradation guard). TTL is a
 * chat-service writer concern, not used here.
 */
@ConfigurationProperties(prefix = "conversation.memory")
public record ConversationMemoryProperties(int size, Boolean enabled) {

    public ConversationMemoryProperties {
        if (size <= 0) {
            size = 10;
        }
        if (enabled == null) {
            enabled = true;
        }
    }
}
