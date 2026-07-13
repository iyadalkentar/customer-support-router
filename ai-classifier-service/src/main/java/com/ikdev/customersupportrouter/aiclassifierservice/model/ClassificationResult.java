package com.ikdev.customersupportrouter.aiclassifierservice.model;

import java.util.UUID;

public record ClassificationResult(
        Long messageId,
        Long conversationId,
        String intent,
        String sentiment,
        String urgency,
        UUID traceId,
        int eventVersion) {

    public static ClassificationResult from(ClassificationFields fields, Long messageId, Long conversationId,
            UUID traceId, int eventVersion) {
        return new ClassificationResult(messageId, conversationId, fields.intent(), fields.sentiment(),
                fields.urgency(), traceId, eventVersion);
    }

    public static ClassificationResult fallback(Long messageId, Long conversationId, UUID traceId,
            int eventVersion) {
        return from(ClassificationFields.FALLBACK, messageId, conversationId, traceId, eventVersion);
    }
}
