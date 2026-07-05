package com.ikdev.customersupportrouter.chatservice.service;

import com.ikdev.customersupportrouter.chatservice.entity.Conversation;
import com.ikdev.customersupportrouter.chatservice.entity.ConversationStatus;
import com.ikdev.customersupportrouter.chatservice.entity.Message;
import com.ikdev.customersupportrouter.chatservice.exception.ConversationClosedException;
import com.ikdev.customersupportrouter.chatservice.exception.ConversationNotFoundException;
import com.ikdev.customersupportrouter.chatservice.repository.ConversationRepository;
import com.ikdev.customersupportrouter.chatservice.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}