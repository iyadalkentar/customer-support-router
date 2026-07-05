package com.ikdev.customersupportrouter.chatservice.controller;

import com.ikdev.customersupportrouter.chatservice.dto.CreateMessageRequest;
import com.ikdev.customersupportrouter.chatservice.dto.MessageResponse;
import com.ikdev.customersupportrouter.chatservice.entity.Message;
import com.ikdev.customersupportrouter.chatservice.service.ConversationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/messages")
public class MessageController {

    private final ConversationService conversationService;

    public MessageController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(@RequestBody @Valid CreateMessageRequest request) {
        Message message = conversationService.addMessageToConversation(request.conversationId(), request.sender(),
                request.content());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(MessageResponse.from(message));
    }
}