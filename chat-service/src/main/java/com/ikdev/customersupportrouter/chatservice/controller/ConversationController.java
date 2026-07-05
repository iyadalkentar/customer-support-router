package com.ikdev.customersupportrouter.chatservice.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.ikdev.customersupportrouter.chatservice.dto.MessageResponse;
import com.ikdev.customersupportrouter.chatservice.service.ConversationService;

@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/{conversationId}/messages")
    public List<MessageResponse> getConversationMessages(@PathVariable Long conversationId) {
        return conversationService.getConversationMessages(conversationId).stream()
                .map(MessageResponse::from)
                .toList();
    }
}
