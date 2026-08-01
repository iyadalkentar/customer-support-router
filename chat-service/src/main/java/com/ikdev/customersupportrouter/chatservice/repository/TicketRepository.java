package com.ikdev.customersupportrouter.chatservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ikdev.customersupportrouter.chatservice.entity.Ticket;
import com.ikdev.customersupportrouter.chatservice.entity.TicketStatus;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    /**
     * Finds the first OPEN ticket for a conversation, used to enforce
     * "one OPEN ticket per conversation" during escalation.
     */
    Optional<Ticket> findFirstByConversationIdAndStatus(Long conversationId, TicketStatus status);

    /** All tickets for a conversation (used by tests/read paths). */
    List<Ticket> findByConversationId(Long conversationId);
}
