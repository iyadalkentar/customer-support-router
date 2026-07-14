package com.ikdev.customersupportrouter.chatservice.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.ikdev.customersupportrouter.chatservice.event.ClassificationResult;
import com.ikdev.customersupportrouter.chatservice.service.ClassificationService;

/**
 * Consumes {@link ClassificationResult}s from the {@code classification-results}
 * Kafka topic (published by {@code ai-classifier-service}) and applies them to
 * the corresponding {@code Message} row via {@link ClassificationService}.
 *
 * <p>Group ID {@code chat-classification-results-group} partitions this consumer
 * from any future chat-service-side consumers and from the topic's
 * publisher-side group. Per-conversation ordering is preserved because
 * {@code ai-classifier-service} keys by {@code conversationId}.
 */
@Component
public class ClassificationResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClassificationResultConsumer.class);

    private final ClassificationService classificationService;

    public ClassificationResultConsumer(ClassificationService classificationService) {
        this.classificationService = classificationService;
    }

    @KafkaListener(topics = "classification-results", groupId = "chat-classification-results-group")
    public void handle(ClassificationResult result) {
        log.debug("Applying classification: messageId={}, conversationId={}, traceId={}",
                result.messageId(), result.conversationId(), result.traceId());
        classificationService.applyClassification(result);
    }
}
