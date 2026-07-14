package com.ikdev.customersupportrouter.aiclassifierservice.kafka;

import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationFields;
import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationResult;
import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationResultEvent;
import com.ikdev.customersupportrouter.aiclassifierservice.event.MessageEvent;
import com.ikdev.customersupportrouter.aiclassifierservice.service.LlmClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MessageEventConsumer {

    private final LlmClient llmClient;
    private final ApplicationEventPublisher applicationEventPublisher;

    public MessageEventConsumer(LlmClient llmClient, ApplicationEventPublisher applicationEventPublisher) {
        this.llmClient = llmClient;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @KafkaListener(topics = "incoming-messages", groupId = "ai-classifier-group")
    public void handle(MessageEvent event) {
        ClassificationFields fields = llmClient.classify(event.content());
        ClassificationResult result = ClassificationResult.from(fields, event.messageId(), event.conversationId(), event.traceId(), event.eventVersion());
        applicationEventPublisher.publishEvent(new ClassificationResultEvent(result));
    }
}
