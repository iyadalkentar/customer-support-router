package com.ikdev.customersupportrouter.aiclassifierservice.kafka;

import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationResult;
import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationResultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link ClassificationResult}s to Kafka after the surrounding transaction
 * commits.
 * The message key is the {@code conversationId} to guarantee ordering per
 * conversation.
 */
@Component
public class ClassificationResultPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClassificationResultPublisher.class);
    private static final String TOPIC = "classification-results";
    
    private final KafkaTemplate<String, ClassificationResult> kafkaTemplate;

    public ClassificationResultPublisher(KafkaTemplate<String, ClassificationResult> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @EventListener()
    public void onMessagePersisted(ClassificationResultEvent classificationResultEvent) {
        ClassificationResult event = classificationResultEvent.getClassificationResult();
        String key = String.valueOf(event.conversationId()); // partition by conversation
        log.info("Publishing Classification Result to Kafka: messageId={}, conversationId={}", event.messageId(),
                event.conversationId());
        kafkaTemplate.send(TOPIC, key, event);
    }
}
