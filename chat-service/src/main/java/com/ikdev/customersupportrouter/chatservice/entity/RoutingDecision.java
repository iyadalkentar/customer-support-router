package com.ikdev.customersupportrouter.chatservice.entity;

/**
 * The routing outcome for a classified message (Phase 4).
 *
 * <p>Both {@code ESCALATE_TO_HUMAN} and {@code CREATE_TICKET} are escalations in
 * the current phase — each creates/reuses an OPEN {@link Ticket} for the
 * conversation and publishes an escalation event. The distinction is preserved
 * in the stored value and in the escalation event so a later phase can
 * differentiate the two (assign an owner, page a human, set priority).
 */
public enum RoutingDecision {
    AUTO_RESPOND,
    ESCALATE_TO_HUMAN,
    CREATE_TICKET;

    public boolean isEscalation() {
        return this != AUTO_RESPOND;
    }
}
