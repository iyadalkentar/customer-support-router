package com.ikdev.customersupportrouter.chatservice.service;

import com.ikdev.customersupportrouter.chatservice.entity.Conversation;
import com.ikdev.customersupportrouter.chatservice.entity.ConversationStatus;
import com.ikdev.customersupportrouter.chatservice.entity.Message;
import com.ikdev.customersupportrouter.chatservice.event.MessageEvent;
import com.ikdev.customersupportrouter.chatservice.event.MessagePersistedEvent;
import com.ikdev.customersupportrouter.chatservice.exception.ConversationClosedException;
import com.ikdev.customersupportrouter.chatservice.exception.ConversationNotFoundException;
import com.ikdev.customersupportrouter.chatservice.repository.ConversationRepository;
import com.ikdev.customersupportrouter.chatservice.repository.MessageRepository;
import com.ikdev.customersupportrouter.chatservice.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ConversationService conversationService;

    @Test
    void getOrCreateConversation_withNullId_createsNewActiveConversation() {
        when(conversationRepository.save(any(Conversation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Conversation result = conversationService.getOrCreateConversation(null);

        assertThat(result.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
        verify(conversationRepository).save(any(Conversation.class));
        verify(conversationRepository, never()).findById(any());
    }

    @Test
    void getOrCreateConversation_withValidActiveId_returnsExisting() {
        Conversation existing = new Conversation();
        existing.setId(1L);
        existing.setStatus(ConversationStatus.ACTIVE);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(existing));

        Conversation result = conversationService.getOrCreateConversation(1L);

        assertThat(result).isSameAs(existing);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void getOrCreateConversation_withUnknownId_throwsNotFound() {
        when(conversationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.getOrCreateConversation(99L))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void getOrCreateConversation_withClosedConversation_throwsClosed() {
        Conversation closed = new Conversation();
        closed.setId(2L);
        closed.setStatus(ConversationStatus.CLOSED);
        when(conversationRepository.findById(2L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> conversationService.getOrCreateConversation(2L))
                .isInstanceOf(ConversationClosedException.class);
    }

    @Test
    void addMessageToConversation_persistsMessageDirectlyAndReturnsIt() {
        Conversation conversation = new Conversation();
        conversation.setId(1L);
        conversation.setStatus(ConversationStatus.ACTIVE);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Message result = conversationService.addMessageToConversation(1L, "customer", "hello");

        assertThat(result.getSender()).isEqualTo("customer");
        assertThat(result.getContent()).isEqualTo("hello");
        assertThat(result.getConversation()).isSameAs(conversation);
        verify(messageRepository).save(result);
    }

    // --- New in Phase 2: event publishing ---
    //
    // Note: this is a plain Mockito unit test, so it only proves that
    // ConversationService calls publishEvent(...) with the right payload.
    // It does NOT exercise the @TransactionalEventListener(AFTER_COMMIT)
    // guarantee on the consumer side (MessageEventPublisher) — that behavior
    // is only meaningfully covered by MessageFlowIntegrationTest, which runs
    // against a real transaction manager and a real Kafka broker.

    @Test
    void addMessageToConversation_publishesMessagePersistedEvent() {
        Conversation conversation = new Conversation();
        conversation.setId(1L);
        conversation.setStatus(ConversationStatus.ACTIVE);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Message result = conversationService.addMessageToConversation(1L, "customer", "hello");

        ArgumentCaptor<MessagePersistedEvent> eventCaptor =
                ArgumentCaptor.forClass(MessagePersistedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        MessageEvent publishedEvent = eventCaptor.getValue().getMessageEvent();
        assertThat(publishedEvent.conversationId()).isEqualTo(1L);
        assertThat(publishedEvent.sender()).isEqualTo("customer");
        assertThat(publishedEvent.content()).isEqualTo("hello");
        assertThat(publishedEvent.messageId()).isEqualTo(result.getId());
    }

    @Test
    void getOrCreateConversation_doesNotPublishAnyEvent() {
        // Guards against accidentally firing a MessagePersistedEvent before a
        // message actually exists (e.g. if event publishing were moved earlier
        // into getOrCreateConversation by mistake in a future refactor).
        when(conversationRepository.save(any(Conversation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        conversationService.getOrCreateConversation(null);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void getAllConversations_delegatesToRepository() {
        Conversation conv1 = new Conversation();
        conv1.setId(1L);
        Conversation conv2 = new Conversation();
        conv2.setId(2L);
        when(conversationRepository.findAllByOrderByCreatedAtDesc(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(java.util.List.of(conv2, conv1));

        var result = conversationService.getAllConversations();

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isSameAs(conv2);
        assertThat(result.get(1)).isSameAs(conv1);
        verify(conversationRepository).findAllByOrderByCreatedAtDesc(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void getConversation_withValidId_returnsConversation() {
        Conversation existing = new Conversation();
        existing.setId(1L);
        when(conversationRepository.findById(1L)).thenReturn(java.util.Optional.of(existing));

        Conversation result = conversationService.getConversation(1L);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void getConversation_withUnknownId_throwsNotFound() {
        when(conversationRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> conversationService.getConversation(99L))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void getConversationTickets_withValidConversationId_delegatesToRepository() {
        when(conversationRepository.existsById(1L)).thenReturn(true);
        java.util.List<com.ikdev.customersupportrouter.chatservice.entity.Ticket> expectedTickets = java.util.List.of();
        when(ticketRepository.findByConversationIdOrderByCreatedAtDesc(1L))
                .thenReturn(expectedTickets);

        var result = conversationService.getConversationTickets(1L);

        assertThat(result).isSameAs(expectedTickets);
        verify(conversationRepository).existsById(1L);
        verify(ticketRepository).findByConversationIdOrderByCreatedAtDesc(1L);
    }

    @Test
    void getConversationTickets_withUnknownConversationId_throwsNotFound() {
        when(conversationRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> conversationService.getConversationTickets(99L))
                .isInstanceOf(ConversationNotFoundException.class);
    }
}