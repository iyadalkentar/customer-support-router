package com.ikdev.customersupportrouter.chatservice.kafka;

import com.ikdev.customersupportrouter.chatservice.event.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Stub consumer for Phase 2. In later phases this will forward the event to the
 * AI classifier service. For now it simply logs the received payload.
 */
@Component
public class MessageEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageEventConsumer.class);

    @KafkaListener(topics = "incoming-messages", groupId = "chat-service-group")
    public void handle(MessageEvent event) {
        log.info("Consumed MessageEvent: messageId={}, conversationId={}, sender={}, content={}",
                event.messageId(), event.conversationId(), event.sender(), event.content());
        // TODO: invoke AI classifier (Phase 3)
    }
}
