package com.ikdev.customersupportrouter.chatservice.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikdev.customersupportrouter.chatservice.entity.Message;
import com.ikdev.customersupportrouter.chatservice.event.ClassificationResult;
import com.ikdev.customersupportrouter.chatservice.repository.MessageRepository;

/**
 * Applies a {@link ClassificationResult} (received from the AI classifier
 * service on the {@code classification-results} Kafka topic) to its
 * corresponding {@link Message} row.
 *
 * <p>Idempotent on redelivery — setting classification fields to the same
 * values is a no-op. Missing messages are logged and dropped: the Phase 3
 * plan explicitly defers DLQ handling.
 */
@Service
public class ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationService.class);

    private final MessageRepository messageRepository;

    public ClassificationService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    /**
     * Applies the classification fields to the message row and returns the
     * managed {@link Message}, so the caller ({@link RoutingService}) can
     * route it in the same transaction. Returns {@link Optional#empty()} when
     * the {@code messageId} is unknown (already logged + dropped).
     */
    @Transactional
    public Optional<Message> applyClassification(ClassificationResult result) {
        Optional<Message> maybeMessage = messageRepository.findById(result.messageId());
        if (maybeMessage.isEmpty()) {
            log.warn("Classification received for unknown messageId={} (conversationId={}, traceId={}); dropping",
                    result.messageId(), result.conversationId(), result.traceId());
            return Optional.empty();
        }

        Message message = maybeMessage.get();
        message.setIntent(result.intent());
        message.setSentiment(result.sentiment());
        message.setUrgency(result.urgency());
        // traceId is not re-set: it is established at message insert (MessageEvent
        // publishes it) and the ClassificationResult carries the same value back.
        // Re-setting on every event would be a no-op.
        messageRepository.save(message);
        return Optional.of(message);
    }
}
