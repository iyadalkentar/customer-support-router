package com.ikdev.customersupportrouter.chatservice.service;

import com.ikdev.customersupportrouter.chatservice.entity.RoutingDecision;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Metrics for routing / escalation decisions in chat-service.
 *
 * <p>Counter {@code escalation.event} (exported as {@code escalation_event_total} —
 * not {@code escalation.created}, which Prometheus's OpenMetrics client reserves and
 * silently strips), tagged by:
 * <ul>
 *   <li>{@code routing_decision} — {@code ESCALATE_TO_HUMAN} or {@code CREATE_TICKET}</li>
 * </ul>
 *
 * <p>No {@code conversationId} tag (unbounded cardinality). Conversation identity belongs in logs, not metric labels.
 * The {@code routing_decision} tag is bounded (enum values), so lookup by tag from registry is safe.
 */
@Component
public class RoutingMetrics {

    // Not "escalation.created" — Prometheus's OpenMetrics client reserves the
    // ".created"/"_created" suffix for its own auto-generated timestamp series and
    // silently strips it from any meter name ending in it, which collapsed this
    // counter to "escalation_total" on export instead of "escalation_created_total".
    static final String ESCALATION_CREATED = "escalation.event";

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