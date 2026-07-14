package com.ikdev.customersupportrouter.aiclassifierservice.kafka;

import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationFields;
import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationResult;
import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationResultEvent;
import com.ikdev.customersupportrouter.aiclassifierservice.event.MessageEvent;
import com.ikdev.customersupportrouter.aiclassifierservice.service.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MessageEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageEventConsumer.class);

    private final LlmClient llmClient;
    private final ApplicationEventPublisher applicationEventPublisher;

    public MessageEventConsumer(LlmClient llmClient, ApplicationEventPublisher applicationEventPublisher) {
        this.llmClient = llmClient;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @KafkaListener(topics = "incoming-messages", groupId = "ai-classifier-group")
    public void handle(MessageEvent event) {
        // @Retryable on the LLM client has already exhausted its single retry by the
        // time control reaches the catch. Per the Phase 3 plan, an explicit fallback is
        // better than letting the message have no classification — publish UNKNOWN/NEUTRAL
        // and move on rather than letting the exception escape to Kafka.
        ClassificationResult result;
        try {
            ClassificationFields fields = llmClient.classify(event.content());
            result = ClassificationResult.from(fields, event.messageId(), event.conversationId(), event.traceId(), event.eventVersion());
        } catch (Exception ex) {
            log.warn("Classification failed after retries for messageId={}, conversationId={}, traceId={}; using fallback",
                    event.messageId(), event.conversationId(), event.traceId(), ex);
            result = ClassificationResult.fallback(event.messageId(), event.conversationId(), event.traceId(), event.eventVersion());
        }
        applicationEventPublisher.publishEvent(new ClassificationResultEvent(result));
    }
}
