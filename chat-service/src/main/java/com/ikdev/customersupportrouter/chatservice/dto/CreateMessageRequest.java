package com.ikdev.customersupportrouter.chatservice.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateMessageRequest(
        Long conversationId,
        @NotBlank String sender,
        @NotBlank String content) {
}