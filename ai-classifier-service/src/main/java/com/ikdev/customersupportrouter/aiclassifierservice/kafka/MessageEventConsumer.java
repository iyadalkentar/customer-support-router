package com.ikdev.customersupportrouter.aiclassifierservice.kafka;

import java.util.List;
import java.util.Objects;

import java.time.Duration;

import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationFields;
import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationResult;
import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationResultEvent;
import com.ikdev.customersupportrouter.aiclassifierservice.event.MessageEvent;
import com.ikdev.customersupportrouter.aiclassifierservice.memory.ConversationContextMessage;
import com.ikdev.customersupportrouter.aiclassifierservice.memory.ConversationMemoryReader;
import com.ikdev.customersupportrouter.aiclassifierservice.service.ChatModelService;
import com.ikdev.customersupportrouter.aiclassifierservice.service.ClassificationMetrics;
import com.ikdev.customersupportrouter.aiclassifierservice.service.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MessageEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageEventConsumer.class);

    private final LlmClient llmClient;
    private final ConversationMemoryReader conversationMemoryReader;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ClassificationMetrics classificationMetrics;
    private final String provider;

    public MessageEventConsumer(LlmClient llmClient, ConversationMemoryReader conversationMemoryReader,
            ApplicationEventPublisher applicationEventPublisher, ClassificationMetrics classificationMetrics,
            @Value("${llm.provider:unknown}") String provider) {
        this.llmClient = llmClient;
        this.conversationMemoryReader = conversationMemoryReader;
        this.applicationEventPublisher = applicationEventPublisher;
        this.classificationMetrics = classificationMetrics;
        this.provider = provider;
    }

    @KafkaListener(topics = "incoming-messages", groupId = "ai-classifier-group")
    public void handle(MessageEvent event) {
        // @Retryable on the LLM client has already exhausted its single retry by the
        // time control reaches the catch. Per the Phase 3 plan, an explicit fallback is
        // better than letting the message have no classification — publish UNKNOWN/NEUTRAL
        // and move on rather than letting the exception escape to Kafka.
        ClassificationResult result;
        long startNanos = System.nanoTime();
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
            // @Retryable has exhausted its retries by the time control reaches here, so this
            // is the true terminal outcome — unlike per-attempt recording inside
            // ChatModelService, this can't mislabel an attempt that was retried into success.
            String outcome = ex instanceof ChatModelService.LlmTimeoutException ? "timeout" : "fallback";
            classificationMetrics.recordClassification(provider, outcome,
                    Duration.ofNanos(System.nanoTime() - startNanos));
            result = ClassificationResult.fallback(event.messageId(), event.conversationId(), event.traceId(), event.eventVersion());
        }
        applicationEventPublisher.publishEvent(new ClassificationResultEvent(result));
    }
}
