package com.ikdev.customersupportrouter.chatservice.exception;

/**
 * Thrown when a conversation exists but its status is CLOSED.
 */
public class ConversationClosedException extends RuntimeException {
    public ConversationClosedException(Long conversationId) {
        super("Conversation with id " + conversationId + " is closed");
    }
}
