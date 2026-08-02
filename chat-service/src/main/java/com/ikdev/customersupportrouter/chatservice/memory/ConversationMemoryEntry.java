package com.ikdev.customersupportrouter.chatservice.memory;

import java.time.OffsetDateTime;

import com.ikdev.customersupportrouter.chatservice.event.MessageEvent;

/**
 * Wire format of one element of the Redis list {@code conversation:{id}:messages}.
 * <p>
 * FIELD NAMES AND TYPES ARE A CROSS-SERVICE CONTRACT — ai-classifier-service's
 * {@code ConversationContextMessage} must stay byte-compatible. Serialized with
 * the Boot Jackson 3 {@code ObjectMapper} (ISO-8601 {@code createdAt}).
 * <p>
 * Deliberately no {@code from(Message)}: the backfill path in
 * {@link ConversationMemoryWriter} must not touch {@code Message#getConversation()}
 * (LAZY association; the session is closed in AFTER_COMMIT). Backfill builds the
 * record from the event's {@code conversationId} plus scalar columns only.
 */
public record ConversationMemoryEntry(
        Long messageId,
        Long conversationId,
        String sender,
        String content,
        OffsetDateTime createdAt) {

    public static ConversationMemoryEntry from(MessageEvent event) {
        return new ConversationMemoryEntry(event.messageId(), event.conversationId(),
                event.sender(), event.content(), event.createdAt());
    }
}
