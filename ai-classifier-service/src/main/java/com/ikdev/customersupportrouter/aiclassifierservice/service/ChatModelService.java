package com.ikdev.customersupportrouter.aiclassifierservice.service;

import com.ikdev.customersupportrouter.aiclassifierservice.config.LlmPromptProperties;
import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationFields;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class ChatModelService implements LlmClient{

    private final ChatClient chatClient;
    private final String classificationPrompt;

    public ChatModelService(ChatModel chatModel, LlmPromptProperties llmPromptProperties) {
        classificationPrompt = llmPromptProperties.resolve(chatModel.getOptions().getModel());
        chatClient = ChatClient.builder(chatModel)
                .build();
    }

    @Retryable(
            value = Exception.class,
            maxRetries = 1
    )
    @Override
    public ClassificationFields classify(String content) {
        return chatClient.prompt()
                .user(u -> u.text(classificationPrompt)
                        .param("message", content))
                .call()
                .entity(ClassificationFields.class);
    }
}
