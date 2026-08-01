package com.ikdev.customersupportrouter.chatservice.service;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.ikdev.customersupportrouter.chatservice.entity.Message;
import com.ikdev.customersupportrouter.chatservice.entity.RoutingDecision;
import com.ikdev.customersupportrouter.chatservice.entity.Ticket;
import com.ikdev.customersupportrouter.chatservice.entity.TicketStatus;
import com.ikdev.customersupportrouter.chatservice.event.EscalationEvent;
import com.ikdev.customersupportrouter.chatservice.event.EscalationPersistedEvent;
import com.ikdev.customersupportrouter.chatservice.repository.MessageRepository;
import com.ikdev.customersupportrouter.chatservice.repository.TicketRepository;

/**
 * Action executor for routing decisions (Phase 4). On escalation: guarantees a
 * single OPEN ticket per conversation and fires an {@link EscalationPersistedEvent}
 * so the escalation can be published to Kafka after commit. On de-escalation
 * (a corrected classification moving the last escalating message back to
 * AUTO_RESPOND): closes the conversation's OPEN ticket.
 *
 * <p>Not itself {@code @Transactional} — it runs inside the caller's
 * transaction (default propagation joins), so the ticket row and the
 * escalation event are atomic with the classification write.
 */
@Service
public class EscalationService {

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);

    /** Decisions that require an OPEN ticket; the complement of AUTO_RESPOND. */
    private static final Collection<RoutingDecision> ESCALATION_DECISIONS = List.of(
            RoutingDecision.ESCALATE_TO_HUMAN, RoutingDecision.CREATE_TICKET);

    private final TicketRepository ticketRepository;
    private final MessageRepository messageRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EscalationService(TicketRepository ticketRepository, MessageRepository messageRepository,
            ApplicationEventPublisher eventPublisher) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.eventPublisher = eventPublisher;
    }

    public void escalate(Message message, RoutingDecision decision) {
        Long conversationId = message.getConversation().getId();
        Ticket ticket = ticketRepository
                .findFirstByConversationIdAndStatus(conversationId, TicketStatus.OPEN)
                .orElseGet(() -> {
                    Ticket created = new Ticket();
                    message.getConversation().addTicket(created);
                    return ticketRepository.save(created);
                });
        eventPublisher.publishEvent(new EscalationPersistedEvent(EscalationEvent.from(ticket, message, decision)));
    }

    /**
     * De-escalation action: closes the conversation's OPEN ticket when no other
     * message in the conversation still carries an escalation decision.
     *
     * <p>Because the ticket is conversation-scoped and reused across escalating
     * messages, it is only closed once the conversation has no remaining
     * escalated message — otherwise a corrected classification on one message
     * could clobber the ticket a still-escalated sibling depends on.
     *
     * <p>No event is published (the {@code escalations} topic only carries
     * escalation notices; a retraction event is deferred with the future
     * consumer). The close is durable in Postgres.
     */
    public void deescalate(Message message) {
        Long conversationId = message.getConversation().getId();
        boolean anyOtherEscalated = messageRepository
                .existsByConversationIdAndRoutingDecisionInAndIdNot(
                        conversationId, ESCALATION_DECISIONS, message.getId());
        if (anyOtherEscalated) {
            log.info("De-escalation keeps OPEN ticket (other messages still escalated): "
                    + "conversationId={}, messageId={}", conversationId, message.getId());
            return;
        }
        ticketRepository.findFirstByConversationIdAndStatus(conversationId, TicketStatus.OPEN)
                .ifPresent(ticket -> {
                    ticket.setStatus(TicketStatus.CLOSED);
                    ticketRepository.save(ticket);
                    log.info("De-escalation closed OPEN ticket: ticketId={}, conversationId={}, messageId={}",
                            ticket.getId(), conversationId, message.getId());
                });
    }
}
