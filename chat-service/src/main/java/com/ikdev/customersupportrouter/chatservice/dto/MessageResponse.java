package com.ikdev.customersupportrouter.chatservice.dto;

import com.ikdev.customersupportrouter.chatservice.entity.Message;

import java.time.OffsetDateTime;

public record MessageResponse(
                Long id,
                Long conversationId,
                String sender,
                String content,
                OffsetDateTime createdAt,
                String intent,
                String sentiment,
                String urgency) {

        public static MessageResponse from(Message message) {
                return new MessageResponse(message.getId(), message.getConversation().getId(),
                                message.getSender(), message.getContent(), message.getCreatedAt(),
                                message.getIntent(), message.getSentiment(), message.getUrgency());
        }
}
