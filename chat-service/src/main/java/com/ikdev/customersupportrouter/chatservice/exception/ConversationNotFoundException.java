package com.ikdev.customersupportrouter.chatservice.exception;

/**
 * Thrown when a conversation with the given ID cannot be found.
 */
public class ConversationNotFoundException extends RuntimeException {
    public ConversationNotFoundException(Long conversationId) {
        super("Conversation with id " + conversationId + " not found");
    }
}
