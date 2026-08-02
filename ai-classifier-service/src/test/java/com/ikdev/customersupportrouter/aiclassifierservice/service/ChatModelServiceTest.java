package com.ikdev.customersupportrouter.aiclassifierservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.mockito.ArgumentCaptor;

import com.ikdev.customersupportrouter.aiclassifierservice.config.LlmPromptProperties;
import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationFields;
import com.ikdev.customersupportrouter.aiclassifierservice.memory.ConversationContextFormatter;
import com.ikdev.customersupportrouter.aiclassifierservice.memory.ConversationContextMessage;

/**
 * Locks the {@code {context}} placeholder ↔ {@code .param("context", ...)} wiring end to
 * end: {@link ChatModelService} must render the real {@code application.yaml} prompt
 * template with the formatted conversation context substituted. Spring AI already throws
 * when a template variable goes unresolved, but this test additionally asserts the actual
 * rendered prompt content — so a placeholder/param mismatch, a dropped context source, or
 * a formatting regression in {@link ConversationContextFormatter} all fail here.
 */
class ChatModelServiceTest {

    @Test
    void classify_rendersConversationContextIntoThePrompt() throws Exception {
        ChatModel chatModel = mockChatModel();
        ChatModelService service = service(chatModel);

        ConversationContextMessage prior = new ConversationContextMessage(1L, 7L, "customer",
                "I cannot log in", OffsetDateTime.parse("2026-08-02T10:00:00Z"));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        when(chatModel.call(promptCaptor.capture()))
                .thenReturn(chatResponse("INFO_REQUEST", "NEUTRAL", "LOW"));

        ClassificationFields result = service.classify("hello", List.of(prior));

        assertThat(result.intent()).isEqualTo("INFO_REQUEST");
        String rendered = promptCaptor.getValue().getContents();
        // The context line and the message both come from {context}/{message} placeholders,
        // so if either placeholder is dropped from the yaml template these assertions fail.
        assertThat(rendered).contains("1. [customer] I cannot log in");
        assertThat(rendered).contains("hello");
    }

    @Test
    void classify_emptyContext_rendersPlaceholderSentence() throws Exception {
        ChatModel chatModel = mockChatModel();
        ChatModelService service = service(chatModel);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        when(chatModel.call(promptCaptor.capture()))
                .thenReturn(chatResponse("UNKNOWN", "NEUTRAL", "UNKNOWN"));

        ClassificationFields result = service.classify("hello", List.of());

        assertThat(result.intent()).isEqualTo("UNKNOWN");
        assertThat(promptCaptor.getValue().getContents()).contains("No prior conversation messages.");
    }

    private static ChatModel mockChatModel() {
        ChatModel chatModel = mock(ChatModel.class);
        // A real ChatOptions (not a mock): the real ChatClient inspects the returned options
        // and a mock breaks its internal toBuilder()/mutate() flow.
        ChatOptions options = ChatOptions.builder().model("test-model").build();
        when(chatModel.getOptions()).thenReturn(options);
        return chatModel;
    }

    private static ChatModelService service(ChatModel chatModel) throws IOException {
        LlmPromptProperties promptProperties = new LlmPromptProperties();
        promptProperties.setDefault(loadDefaultPromptTemplate());
        return new ChatModelService(chatModel, promptProperties, new ConversationContextFormatter(),
                Duration.ofSeconds(5));
    }

    private static ChatResponse chatResponse(String intent, String sentiment, String urgency) {
        String json = "{\"intent\":\"" + intent + "\",\"sentiment\":\"" + sentiment
                + "\",\"urgency\":\"" + urgency + "\"}";
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

    private static String loadDefaultPromptTemplate() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        return (String) sources.get(0).getProperty("llm.prompt.default");
    }
}
