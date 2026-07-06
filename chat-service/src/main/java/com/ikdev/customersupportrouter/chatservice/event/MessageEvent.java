package com.ikdev.customersupportrouter.chatservice.event;

import java.time.OffsetDateTime;

/**
 * Full‑payload representation of a message that will be sent to Kafka.
 * Includes an {@code eventVersion} field for future‑proofing.
 */
public record MessageEvent(
        Long messageId,
        Long conversationId,
        String sender,
        String content,
        OffsetDateTime createdAt,
        int eventVersion
) {
    private static final int CURRENT_VERSION = 1;

    public static MessageEvent from(com.ikdev.customersupportrouter.chatservice.entity.Message message) {
        return new MessageEvent(
                message.getId(),
                message.getConversation().getId(),
                message.getSender(),
                message.getContent(),
                message.getCreatedAt(),
                CURRENT_VERSION
        );
    }
}
