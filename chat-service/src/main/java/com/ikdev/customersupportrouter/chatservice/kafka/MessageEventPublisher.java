package com.ikdev.customersupportrouter.chatservice.kafka;

import com.ikdev.customersupportrouter.chatservice.event.MessageEvent;
import com.ikdev.customersupportrouter.chatservice.event.MessagePersistedEvent;
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

    public MessageEventPublisher(KafkaTemplate<String, MessageEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessagePersisted(MessagePersistedEvent persistedEvent) {
        MessageEvent event = persistedEvent.getMessageEvent();
        String key = String.valueOf(event.conversationId()); // partition by conversation
        log.info("Publishing MessageEvent to Kafka: messageId={}, conversationId={}", event.messageId(),
                event.conversationId());
        // Using the KafkaTemplate with key/value serializers (configured in
        // application.yml)
        kafkaTemplate.send(TOPIC, key, event);
    }
}
