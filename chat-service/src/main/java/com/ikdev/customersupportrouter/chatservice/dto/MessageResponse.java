package com.ikdev.customersupportrouter.chatservice.dto;

import java.time.OffsetDateTime;

import com.ikdev.customersupportrouter.chatservice.entity.Message;

import lombok.Getter;

@Getter
public class MessageResponse {
        private Long id;
        private Long conversationId;
        private String sender;
        private String content;
        private OffsetDateTime createdAt;

        private MessageResponse(Long id, Long conversationId, String sender, String content, OffsetDateTime createdAt) {
                this.id = id;
                this.conversationId = conversationId;
                this.sender = sender;
                this.content = content;
                this.createdAt = createdAt;
        }

        public static MessageResponse from(Message message) {
                return new MessageResponse(message.getId(), message.getConversation().getId(), message.getSender(),
                                message.getContent(), message.getCreatedAt());
        }
}