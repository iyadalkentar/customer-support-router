package com.ikdev.customersupportrouter.chatservice.dto;

import com.ikdev.customersupportrouter.chatservice.entity.Conversation;

import java.time.OffsetDateTime;

public record ConversationResponse(
                Long id,
                String status,
                OffsetDateTime createdAt,
                OffsetDateTime updatedAt) {

        public static ConversationResponse from(Conversation conversation) {
                return new ConversationResponse(conversation.getId(),
                                conversation.getStatus() == null ? null : conversation.getStatus().name(),
                                conversation.getCreatedAt(), conversation.getUpdatedAt());
        }
}
