package com.ikdev.customersupportrouter.chatservice.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikdev.customersupportrouter.chatservice.entity.Conversation;
import com.ikdev.customersupportrouter.chatservice.entity.ConversationStatus;
import com.ikdev.customersupportrouter.chatservice.entity.Message;
import com.ikdev.customersupportrouter.chatservice.exception.ConversationNotFoundException;
import com.ikdev.customersupportrouter.chatservice.exception.ConversationClosedException;
import com.ikdev.customersupportrouter.chatservice.repository.ConversationRepository;
import com.ikdev.customersupportrouter.chatservice.repository.MessageRepository;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationService(ConversationRepository conversationRepository, MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
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
        conversation.addMessage(message);
        messageRepository.save(message);
        conversationRepository.save(conversation);
        return message;
    }

    @Transactional
    public List<Message> getConversationMessages(Long conversationId) {
        if (!conversationRepository.existsById(conversationId))
            throw new ConversationNotFoundException(conversationId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }
}
