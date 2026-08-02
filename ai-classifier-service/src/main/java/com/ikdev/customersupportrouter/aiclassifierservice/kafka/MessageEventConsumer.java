package com.ikdev.customersupportrouter.aiclassifierservice.kafka;

import java.util.List;
import java.util.Objects;

import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationFields;
import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationResult;
import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationResultEvent;
import com.ikdev.customersupportrouter.aiclassifierservice.event.MessageEvent;
import com.ikdev.customersupportrouter.aiclassifierservice.memory.ConversationContextMessage;
import com.ikdev.customersupportrouter.aiclassifierservice.memory.ConversationMemoryReader;
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
    private final ConversationMemoryReader conversationMemoryReader;
    private final ApplicationEventPublisher applicationEventPublisher;

    public MessageEventConsumer(LlmClient llmClient, ConversationMemoryReader conversationMemoryReader,
            ApplicationEventPublisher applicationEventPublisher) {
        this.llmClient = llmClient;
        this.conversationMemoryReader = conversationMemoryReader;
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
            // Context = strictly PRIOR turns. The writer's AFTER_COMMIT append may or may not
        // have landed before this consumer runs; excluding the current message removes that
        // nondeterminism and avoids feeding the LLM the very message it is classifying.
        List<ConversationContextMessage> context = conversationMemoryReader.getRecent(event.conversationId())
                .stream()
                // Objects.equals: a null messageId on a corrupt/foreign entry must not NPE the
                // filter (and cannot be the current message anyway, so it stays in context).
                .filter(m -> !Objects.equals(m.messageId(), event.messageId()))
                .toList();
        ClassificationFields fields = llmClient.classify(event.content(), context);
            result = ClassificationResult.from(fields, event.messageId(), event.conversationId(), event.traceId(), event.eventVersion());
        } catch (Exception ex) {
            log.warn("Classification failed after retries for messageId={}, conversationId={}, traceId={}; using fallback",
                    event.messageId(), event.conversationId(), event.traceId(), ex);
            result = ClassificationResult.fallback(event.messageId(), event.conversationId(), event.traceId(), event.eventVersion());
        }
        applicationEventPublisher.publishEvent(new ClassificationResultEvent(result));
    }
}
