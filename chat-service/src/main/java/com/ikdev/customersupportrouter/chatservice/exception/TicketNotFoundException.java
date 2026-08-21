package com.ikdev.customersupportrouter.chatservice.exception;

/**
 * Thrown when a ticket with the given ID cannot be found.
 */
public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException(Long ticketId) {
        super("Ticket with id " + ticketId + " not found");
    }
}
