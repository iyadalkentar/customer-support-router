package com.ikdev.customersupportrouter.chatservice.service;

import com.ikdev.customersupportrouter.chatservice.entity.RoutingDecision;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Metrics for routing / escalation decisions in chat-service.
 *
 * <p>Counter {@code escalation.created}, tagged by:
 * <ul>
 *   <li>{@code routing_decision} — {@code ESCALATE_TO_HUMAN} or {@code CREATE_TICKET}</li>
 * </ul>
 *
 * <p>No {@code conversationId} tag (unbounded cardinality). Conversation identity belongs in logs, not metric labels.
 * The {@code routing_decision} tag is bounded (enum values), so lookup by tag from registry is safe.
 */
@Component
public class RoutingMetrics {

    static final String ESCALATION_CREATED = "escalation.created";

    private final MeterRegistry meterRegistry;

    public RoutingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records an escalation event for the given routing decision.
     */
    public void recordEscalation(RoutingDecision decision) {
        if (decision == null || !decision.isEscalation()) {
            return; // only count escalations
        }
        meterRegistry.counter(ESCALATION_CREATED,
                "routing_decision", decision.name())
                .increment();
    }
}