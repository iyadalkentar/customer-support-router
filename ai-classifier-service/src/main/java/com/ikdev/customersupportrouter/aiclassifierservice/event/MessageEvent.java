package com.ikdev.customersupportrouter.aiclassifierservice.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MessageEvent(
        Long messageId,
        Long conversationId,
        String sender,
        String content,
        OffsetDateTime createdAt,
        int eventVersion,
        UUID traceId) {
}