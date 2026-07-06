package com.ikdev.customersupportrouter.chatservice.event;

import org.springframework.context.ApplicationEvent;

/**
 * Spring application event that wraps a {@link MessageEvent}.
 * It is published by
 * {@link com.ikdev.customersupportrouter.chatservice.service.ConversationService}
 * after a message has been persisted. The
 * {@link com.ikdev.customersupportrouter.chatservice.kafka.MessageEventPublisher}
 * listens for this event and forwards it to Kafka after the transaction
 * commits.
 */
public class MessagePersistedEvent extends ApplicationEvent {
    private final MessageEvent messageEvent;

    public MessagePersistedEvent(MessageEvent messageEvent) {
        super(messageEvent);
        this.messageEvent = messageEvent;
    }

    public MessageEvent getMessageEvent() {
        return messageEvent;
    }
}
