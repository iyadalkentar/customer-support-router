package com.ikdev.customersupportrouter.chatservice.kafka;

import com.ikdev.customersupportrouter.chatservice.event.MessageEvent;
import com.ikdev.customersupportrouter.chatservice.event.MessagePersistedEvent;
import com.ikdev.customersupportrouter.chatservice.service.MessageMetrics;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes {@link MessageEvent}s to Kafka after the surrounding transaction
 * commits.
 * The message key is the {@code conversationId} to guarantee ordering per
 * conversation.
 */
@Component
public class MessageEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MessageEventPublisher.class);
    private static final String TOPIC = "incoming-messages";

    private final KafkaTemplate<String, MessageEvent> kafkaTemplate;
    private final MessageMetrics messageMetrics;

    public MessageEventPublisher(KafkaTemplate<String, MessageEvent> kafkaTemplate, MessageMetrics messageMetrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.messageMetrics = messageMetrics;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessagePersisted(MessagePersistedEvent persistedEvent) {
        MessageEvent event = persistedEvent.getMessageEvent();
        // Fired only after the enclosing transaction commits, so the counter can't
        // overcount a message that was recorded here but never actually persisted.
        messageMetrics.recordIngest("success");
        String key = String.valueOf(event.conversationId()); // partition by conversation
        log.info("Publishing MessageEvent to Kafka: messageId={}, conversationId={}", event.messageId(),
                event.conversationId());
        // Using the KafkaTemplate with key/value serializers (configured in
        // application.yml)
        // Best-effort publish (same exposure as EscalationEventPublisher): on
        // failure log loudly for manual republish rather than throw out of the
        // AFTER_COMMIT callback.
        kafkaTemplate.send(TOPIC, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("MessageEvent publish FAILED — manual republish needed. "
                                + "messageId={}, conversationId={}. The message was not forwarded "
                                + "to ai-classifier-service for classification.",
                                event.messageId(), event.conversationId(), ex);
                    }
                });
    }
}
