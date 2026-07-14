package com.ikdev.customersupportrouter.chatservice.event;

import java.util.UUID;

public record ClassificationResult(
        Long messageId,
        Long conversationId,
        String intent,
        String sentiment,
        String urgency,
        UUID traceId,
        int eventVersion) {
}