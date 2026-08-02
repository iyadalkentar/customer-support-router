package com.ikdev.customersupportrouter.aiclassifierservice.memory;

import java.time.OffsetDateTime;

/**
 * Cross-service copy of chat-service's {@code ConversationMemoryEntry}. MUST
 * stay field-compatible (messageId, conversationId, sender, content, createdAt)
 * so entries written by chat-service's writer parse here unchanged. Boot's
 * ObjectMapper ignores unknown properties, so minor drift is tolerated — keep
 * them identical anyway.
 */
public record ConversationContextMessage(
        Long messageId,
        Long conversationId,
        String sender,
        String content,
        OffsetDateTime createdAt) {
}
