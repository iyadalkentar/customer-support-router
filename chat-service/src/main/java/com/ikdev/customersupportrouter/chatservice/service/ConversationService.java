package com.ikdev.customersupportrouter.chatservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikdev.customersupportrouter.chatservice.entity.Conversation;
import com.ikdev.customersupportrouter.chatservice.entity.ConversationStatus;
import com.ikdev.customersupportrouter.chatservice.entity.Message;
import com.ikdev.customersupportrouter.chatservice.entity.Ticket;
import com.ikdev.customersupportrouter.chatservice.exception.ConversationNotFoundException;
import com.ikdev.customersupportrouter.chatservice.exception.ConversationClosedException;
import com.ikdev.customersupportrouter.chatservice.repository.ConversationRepository;
import com.ikdev.customersupportrouter.chatservice.repository.MessageRepository;
import com.ikdev.customersupportrouter.chatservice.repository.TicketRepository;
import org.springframework.context.ApplicationEventPublisher;
import com.ikdev.customersupportrouter.chatservice.event.MessageEvent;
import com.ikdev.customersupportrouter.chatservice.event.MessagePersistedEvent;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** Caps unfiltered list responses so they don't grow unbounded as conversations accumulate. */
    private static final Pageable MAX_RESULTS = PageRequest.of(0, 200, Sort.unsorted());

    public ConversationService(ConversationRepository conversationRepository, MessageRepository messageRepository,
            TicketRepository ticketRepository, ApplicationEventPublisher eventPublisher) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Conversation getOrCreateConversation(Long conversationId) {
        if (conversationId == null) {
            Conversation newConversation = new Conversation(); // defaults to ACTIVE
            return conversationRepository.save(newConversation);
        }
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            throw new ConversationClosedException(conversationId);
        }
        return conversation;
    }

    @Transactional
    public Message addMessageToConversation(Long conversationId, String sender,
            String content) {
        Conversation conversation = getOrCreateConversation(conversationId);
        Message message = new Message();
        message.setContent(content);
        message.setSender(sender);
        message.setTraceId(UUID.randomUUID());
        conversation.addMessage(message);
        messageRepository.save(message);
        conversationRepository.save(conversation);
        eventPublisher.publishEvent(new MessagePersistedEvent(MessageEvent.from(message)));
        return message;
    }

    @Transactional
    public List<Message> getConversationMessages(Long conversationId) {
        if (!conversationRepository.existsById(conversationId))
            throw new ConversationNotFoundException(conversationId);
        return messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId);
    }

    @Transactional
    public List<Conversation> getAllConversations() {
        return conversationRepository.findAllByOrderByCreatedAtDesc(MAX_RESULTS);
    }

    @Transactional
    public Conversation getConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
    }

    @Transactional
    public List<Ticket> getConversationTickets(Long conversationId) {
        if (!conversationRepository.existsById(conversationId))
            throw new ConversationNotFoundException(conversationId);
        return ticketRepository.findByConversationIdOrderByCreatedAtDesc(conversationId);
    }
}
