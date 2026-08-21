package com.ikdev.customersupportrouter.chatservice.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.ikdev.customersupportrouter.chatservice.dto.MessageResponse;
import com.ikdev.customersupportrouter.chatservice.dto.ConversationResponse;
import com.ikdev.customersupportrouter.chatservice.dto.TicketResponse;
import com.ikdev.customersupportrouter.chatservice.service.ConversationService;

@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public List<ConversationResponse> getAllConversations() {
        return conversationService.getAllConversations().stream()
                .map(ConversationResponse::from)
                .toList();
    }

    @GetMapping("/{conversationId}")
    public ConversationResponse getConversation(@PathVariable Long conversationId) {
        return ConversationResponse.from(conversationService.getConversation(conversationId));
    }

    @GetMapping("/{conversationId}/messages")
    public List<MessageResponse> getConversationMessages(@PathVariable Long conversationId) {
        return conversationService.getConversationMessages(conversationId).stream()
                .map(MessageResponse::from)
                .toList();
    }

    @GetMapping("/{conversationId}/tickets")
    public List<TicketResponse> getConversationTickets(@PathVariable Long conversationId) {
        return conversationService.getConversationTickets(conversationId).stream()
                .map(TicketResponse::from)
                .toList();
    }
}
