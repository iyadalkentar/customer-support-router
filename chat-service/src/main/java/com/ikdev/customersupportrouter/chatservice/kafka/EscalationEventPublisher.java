package com.ikdev.customersupportrouter.chatservice.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ikdev.customersupportrouter.chatservice.event.EscalationEvent;
import com.ikdev.customersupportrouter.chatservice.event.EscalationPersistedEvent;

/**
 * Publishes {@link EscalationEvent}s to Kafka after the surrounding transaction
 * commits. The message key is the {@code conversationId} to guarantee ordering
 * per conversation (mirrors {@link MessageEventPublisher}).
 */
@Component
public class EscalationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EscalationEventPublisher.class);
    private static final String TOPIC = "escalations";

    private final KafkaTemplate<String, EscalationEvent> kafkaTemplate;

    public EscalationEventPublisher(KafkaTemplate<String, EscalationEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEscalationPersisted(EscalationPersistedEvent persistedEvent) {
        EscalationEvent event = persistedEvent.getEscalationEvent();
        String key = String.valueOf(event.conversationId()); // partition by conversation
        log.info("Publishing EscalationEvent to Kafka: ticketId={}, conversationId={}, messageId={}",
                event.ticketId(), event.conversationId(), event.messageId());
        // Best-effort publish. The OPEN ticket row is durable (same transaction as
        // the classification); only this notification is at risk, so on failure log
        // loudly for manual republish rather than throw out of the AFTER_COMMIT
        // callback (which would trigger a pointless redelivery — the guard in
        // RoutingService skips it because the stored decision is unchanged).
        kafkaTemplate.send(TOPIC, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("EscalationEvent publish FAILED — manual republish needed. "
                                + "ticketId={}, conversationId={}, messageId={}. The OPEN ticket "
                                + "was created but the escalations topic was not notified.",
                                event.ticketId(), event.conversationId(), event.messageId(), ex);
                    }
                });
    }
}
