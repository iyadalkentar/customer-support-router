package com.ikdev.customersupportrouter.chatservice.event;

import java.util.UUID;

import com.ikdev.customersupportrouter.chatservice.entity.Message;
import com.ikdev.customersupportrouter.chatservice.entity.RoutingDecision;
import com.ikdev.customersupportrouter.chatservice.entity.Ticket;

/**
 * Full-payload representation of an escalation, published to the
 * {@code escalations} Kafka topic after the surrounding transaction commits.
 *
 * <p>Carries the classification and the {@link RoutingDecision} so downstream
 * consumers can differentiate {@code ESCALATE_TO_HUMAN} from
 * {@code CREATE_TICKET} without a Postgres round-trip.
 */
public record EscalationEvent(
        Long ticketId,
        Long conversationId,
        Long messageId,
        UUID traceId,
        String intent,
        String sentiment,
        String urgency,
        RoutingDecision routingDecision,
        int eventVersion) {

    private static final int CURRENT_VERSION = 1;

    public static EscalationEvent from(Ticket ticket, Message message, RoutingDecision decision) {
        return new EscalationEvent(
                ticket.getId(),
                message.getConversation().getId(),
                message.getId(),
                message.getTraceId(),
                message.getIntent(),
                message.getSentiment(),
                message.getUrgency(),
                decision,
                CURRENT_VERSION);
    }
}
