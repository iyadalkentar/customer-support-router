package com.ikdev.customersupportrouter.chatservice.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikdev.customersupportrouter.chatservice.entity.Message;
import com.ikdev.customersupportrouter.chatservice.entity.RoutingDecision;
import com.ikdev.customersupportrouter.chatservice.event.ClassificationResult;
import com.ikdev.customersupportrouter.chatservice.repository.MessageRepository;

/**
 * Orchestrates the Phase 4 routing flow: applies an incoming
 * {@link ClassificationResult} to its {@link Message} row, derives the routing
 * decision, records it on the message, and fires the escalation action when the
 * decision is an escalation.
 *
 * <p>Runs inside a single transaction so the classification fields, the
 * {@code routing_decision} column, and (on escalation) the ticket row are
 * atomic with each other. The Kafka escalation publish stays decoupled via the
 * AFTER_COMMIT outbox pattern.
 *
 * <p>Idempotent on redelivery: an escalation is fired only when the stored
 * decision differs from the newly derived one, so re-applying the same result
 * neither re-creates a ticket nor re-publishes an escalation event. The
 * symmetric de-escalation is handled too: a decision moving back to
 * {@code AUTO_RESPOND} asks {@link EscalationService} to close the
 * conversation's OPEN ticket once no message in the conversation escalates.
 */
@Service
public class RoutingService {

    private final ClassificationService classificationService;
    private final RoutingPolicy routingPolicy;
    private final MessageRepository messageRepository;
    private final EscalationService escalationService;
    private final RoutingMetrics routingMetrics;

    public RoutingService(ClassificationService classificationService, RoutingPolicy routingPolicy,
            MessageRepository messageRepository, EscalationService escalationService,
            RoutingMetrics routingMetrics) {
        this.classificationService = classificationService;
        this.routingPolicy = routingPolicy;
        this.messageRepository = messageRepository;
        this.escalationService = escalationService;
        this.routingMetrics = routingMetrics;
    }

    @Transactional
    public void applyClassificationAndRoute(ClassificationResult result) {
        Optional<Message> maybeMessage = classificationService.applyClassification(result);
        if (maybeMessage.isEmpty()) {
            // Unknown messageId already logged + dropped in ClassificationService.
            return;
        }

        Message message = maybeMessage.get();
        RoutingDecision previous = message.getRoutingDecision();
        RoutingDecision decision = routingPolicy.decide(
                message.getIntent(), message.getSentiment(), message.getUrgency());

        message.setRoutingDecision(decision);
        if (previous == decision) {
            // No change — at-least-once redelivery no-op (also avoids re-escalation).
        } else if (decision.isEscalation()) {
            escalationService.escalate(message, decision);
            routingMetrics.recordEscalation(decision);
        } else if (previous != null && previous.isEscalation()) {
            // De-escalation: a corrected/re-classified message no longer escalates;
            // close the conversation's OPEN ticket once no message still escalates.
            escalationService.deescalate(message);
        }
        messageRepository.save(message);
    }
}
