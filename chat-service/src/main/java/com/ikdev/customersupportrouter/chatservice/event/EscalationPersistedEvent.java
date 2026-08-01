package com.ikdev.customersupportrouter.chatservice.event;

import org.springframework.context.ApplicationEvent;

/**
 * Spring application event that wraps an {@link EscalationEvent}. Published by
 * {@link com.ikdev.customersupportrouter.chatservice.service.EscalationService}
 * inside the routing transaction; the
 * {@link com.ikdev.customersupportrouter.chatservice.kafka.EscalationEventPublisher}
 * listens and forwards it to the {@code escalations} Kafka topic after the
 * transaction commits.
 */
public class EscalationPersistedEvent extends ApplicationEvent {
    private final EscalationEvent escalationEvent;

    public EscalationPersistedEvent(EscalationEvent escalationEvent) {
        super(escalationEvent);
        this.escalationEvent = escalationEvent;
    }

    public EscalationEvent getEscalationEvent() {
        return escalationEvent;
    }
}
