package com.ikdev.customersupportrouter.chatservice.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.ikdev.customersupportrouter.chatservice.event.ClassificationResult;
import com.ikdev.customersupportrouter.chatservice.service.RoutingService;

/**
 * Consumes {@link ClassificationResult}s from the {@code classification-results}
 * Kafka topic (published by {@code ai-classifier-service}) and routes them:
 * applies the classification to the {@code Message} row and, based on the
 * derived {@code routing_decision}, may escalate (create/reuse a ticket and
 * publish an escalation event).
 *
 * <p>Group ID {@code chat-classification-results-group} partitions this consumer
 * from any future chat-service-side consumers and from the topic's
 * publisher-side group. Per-conversation ordering is preserved because
 * {@code ai-classifier-service} keys by {@code conversationId}.
 */
@Component
public class ClassificationResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClassificationResultConsumer.class);

    private final RoutingService routingService;

    public ClassificationResultConsumer(RoutingService routingService) {
        this.routingService = routingService;
    }

    @KafkaListener(topics = "classification-results", groupId = "chat-classification-results-group")
    public void handle(ClassificationResult result) {
        log.debug("Routing classification: messageId={}, conversationId={}, traceId={}",
                result.messageId(), result.conversationId(), result.traceId());
        routingService.applyClassificationAndRoute(result);
    }
}
