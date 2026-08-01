package com.ikdev.customersupportrouter.chatservice.service;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.ikdev.customersupportrouter.chatservice.entity.Conversation;
import com.ikdev.customersupportrouter.chatservice.entity.Message;
import com.ikdev.customersupportrouter.chatservice.entity.RoutingDecision;
import com.ikdev.customersupportrouter.chatservice.entity.Ticket;
import com.ikdev.customersupportrouter.chatservice.entity.TicketStatus;
import com.ikdev.customersupportrouter.chatservice.event.EscalationEvent;
import com.ikdev.customersupportrouter.chatservice.event.EscalationPersistedEvent;
import com.ikdev.customersupportrouter.chatservice.repository.MessageRepository;
import com.ikdev.customersupportrouter.chatservice.repository.TicketRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscalationServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private EscalationService escalationService;

    private Message message(long messageId, long conversationId) {
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        Message message = new Message();
        message.setId(messageId);
        message.setConversation(conversation);
        message.setTraceId(UUID.randomUUID());
        message.setIntent("COMPLAINT");
        message.setSentiment("NEGATIVE");
        message.setUrgency("HIGH");
        return message;
    }

    @Test
    void escalate_createsNewOpenTicket_whenNoneOpen_andPublishesEvent() {
        Message message = message(42L, 7L);
        when(ticketRepository.findFirstByConversationIdAndStatus(7L, TicketStatus.OPEN))
                .thenReturn(Optional.empty());
        when(ticketRepository.save(any(Ticket.class)))
                .thenAnswer(invocation -> {
                    Ticket saved = invocation.getArgument(0);
                    saved.setId(500L);
                    return saved;
                });

        escalationService.escalate(message, RoutingDecision.ESCALATE_TO_HUMAN);

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticketCaptor.getValue().getConversation()).isSameAs(message.getConversation());

        assertThat(publishedEvent().ticketId()).isEqualTo(500L);
    }

    @Test
    void escalate_reusesExistingOpenTicket_noSaveButPublishesEvent() {
        Message message = message(42L, 7L);
        Ticket existing = new Ticket();
        existing.setId(900L);
        existing.setStatus(TicketStatus.OPEN);
        when(ticketRepository.findFirstByConversationIdAndStatus(7L, TicketStatus.OPEN))
                .thenReturn(Optional.of(existing));

        escalationService.escalate(message, RoutingDecision.CREATE_TICKET);

        verify(ticketRepository, never()).save(any());
        EscalationEvent event = publishedEvent();
        assertThat(event.ticketId()).isEqualTo(900L);
        assertThat(event.routingDecision()).isEqualTo(RoutingDecision.CREATE_TICKET);
    }

    @Test
    void escalate_publishesEventWithFullEscalationPayload() {
        Message message = message(42L, 7L);
        when(ticketRepository.findFirstByConversationIdAndStatus(7L, TicketStatus.OPEN))
                .thenReturn(Optional.empty());
        when(ticketRepository.save(any(Ticket.class)))
                .thenAnswer(invocation -> {
                    Ticket saved = invocation.getArgument(0);
                    saved.setId(500L);
                    return saved;
                });

        escalationService.escalate(message, RoutingDecision.ESCALATE_TO_HUMAN);

        EscalationEvent event = publishedEvent();
        assertThat(event.conversationId()).isEqualTo(7L);
        assertThat(event.messageId()).isEqualTo(42L);
        assertThat(event.traceId()).isEqualTo(message.getTraceId());
        assertThat(event.intent()).isEqualTo("COMPLAINT");
        assertThat(event.sentiment()).isEqualTo("NEGATIVE");
        assertThat(event.urgency()).isEqualTo("HIGH");
        assertThat(event.routingDecision()).isEqualTo(RoutingDecision.ESCALATE_TO_HUMAN);
        assertThat(event.eventVersion()).isEqualTo(1);
    }

    @Test
    void deescalate_closesOpenTicket_whenNoOtherMessageStillEscalated() {
        Message message = message(42L, 7L);
        when(messageRepository.existsByConversationIdAndRoutingDecisionInAndIdNot(
                eq(7L), anyCollection(), eq(42L))).thenReturn(false);
        Ticket existing = new Ticket();
        existing.setId(900L);
        existing.setStatus(TicketStatus.OPEN);
        when(ticketRepository.findFirstByConversationIdAndStatus(7L, TicketStatus.OPEN))
                .thenReturn(Optional.of(existing));

        escalationService.deescalate(message);

        assertThat(existing.getStatus()).isEqualTo(TicketStatus.CLOSED);
        verify(ticketRepository).save(existing);
    }

    @Test
    void deescalate_keepsTicketOpen_whenAnotherMessageStillEscalated() {
        Message message = message(42L, 7L);
        when(messageRepository.existsByConversationIdAndRoutingDecisionInAndIdNot(
                eq(7L), anyCollection(), eq(42L))).thenReturn(true);

        escalationService.deescalate(message);

        verify(ticketRepository, never()).findFirstByConversationIdAndStatus(anyLong(), any());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void deescalate_noOpenTicket_doesNothing() {
        Message message = message(42L, 7L);
        when(messageRepository.existsByConversationIdAndRoutingDecisionInAndIdNot(
                eq(7L), anyCollection(), eq(42L))).thenReturn(false);
        when(ticketRepository.findFirstByConversationIdAndStatus(7L, TicketStatus.OPEN))
                .thenReturn(Optional.empty());

        escalationService.deescalate(message);

        verify(ticketRepository, never()).save(any());
    }

    private EscalationEvent publishedEvent() {
        ArgumentCaptor<EscalationPersistedEvent> eventCaptor =
                ArgumentCaptor.forClass(EscalationPersistedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        return eventCaptor.getValue().getEscalationEvent();
    }
}
