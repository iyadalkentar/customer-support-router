package com.ikdev.customersupportrouter.chatservice.dto;

import com.ikdev.customersupportrouter.chatservice.entity.Ticket;

import java.time.OffsetDateTime;

public record TicketResponse(
                Long id,
                Long conversationId,
                String status,
                OffsetDateTime createdAt,
                OffsetDateTime updatedAt) {

        public static TicketResponse from(Ticket ticket) {
                return new TicketResponse(ticket.getId(), ticket.getConversation().getId(),
                                ticket.getStatus() == null ? null : ticket.getStatus().name(),
                                ticket.getCreatedAt(), ticket.getUpdatedAt());
        }
}
