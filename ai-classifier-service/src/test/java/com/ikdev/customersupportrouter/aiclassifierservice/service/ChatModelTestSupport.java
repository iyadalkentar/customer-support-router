package com.ikdev.customersupportrouter.aiclassifierservice.service;

import java.io.IOException;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared fixtures for {@link ChatModelService} unit tests: a mocked {@link ChatModel},
 * a synthetic classification {@link ChatResponse}, and the real {@code llm.prompt.default}
 * template loaded from the classpath {@code application.yaml}.
 */
final class ChatModelTestSupport {

    private ChatModelTestSupport() {
    }

    static ChatModel mockChatModel() {
        ChatModel chatModel = mock(ChatModel.class);
        // A real ChatOptions (not a mock): the real ChatClient inspects the returned options
        // and a mock breaks its internal toBuilder()/mutate() flow.
        ChatOptions options = ChatOptions.builder().model("test-model").build();
        when(chatModel.getOptions()).thenReturn(options);
        return chatModel;
    }

    static ChatResponse chatResponse(String intent, String sentiment, String urgency) {
        String json = "{\"intent\":\"" + intent + "\",\"sentiment\":\"" + sentiment
                + "\",\"urgency\":\"" + urgency + "\"}";
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

    static String loadDefaultPromptTemplate() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        return (String) sources.get(0).getProperty("llm.prompt.default");
    }
}
