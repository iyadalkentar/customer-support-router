package com.ikdev.customersupportrouter.aiclassifierservice.memory;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Renders conversation context into the prompt's {@code {context}} placeholder.
 * Pure and unit-testable without a {@code ChatModel}. The empty case returns a
 * real sentence (never a blank line) so the placeholder stays well-formed when
 * Redis is down, the key is missing, or it is the first message.
 */
@Component
public class ConversationContextFormatter {

    public String format(List<ConversationContextMessage> context) {
        if (context == null || context.isEmpty()) {
            return "No prior conversation messages.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < context.size(); i++) {
            ConversationContextMessage message = context.get(i);
            sb.append(i + 1).append(". [").append(message.sender()).append("] ").append(message.content()).append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
