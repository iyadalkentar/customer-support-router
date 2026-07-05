package com.ikdev.customersupportrouter.chatservice.entity;

/**
 * Enum representing possible statuses of a tickets.
 * The default status is {@code OPEN}.
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
}