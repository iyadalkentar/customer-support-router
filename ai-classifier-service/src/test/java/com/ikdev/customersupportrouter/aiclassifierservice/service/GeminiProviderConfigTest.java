package com.ikdev.customersupportrouter.aiclassifierservice.service;

import static com.ikdev.customersupportrouter.aiclassifierservice.service.ChatModelTestSupport.chatResponse;
import static com.ikdev.customersupportrouter.aiclassifierservice.service.ChatModelTestSupport.loadDefaultPromptTemplate;
import static com.ikdev.customersupportrouter.aiclassifierservice.service.ChatModelTestSupport.mockChatModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.mockito.ArgumentCaptor;

import com.ikdev.customersupportrouter.aiclassifierservice.config.LlmPromptProperties;
import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationFields;
import com.ikdev.customersupportrouter.aiclassifierservice.memory.ConversationContextFormatter;
import com.ikdev.customersupportrouter.aiclassifierservice.memory.ConversationContextMessage;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Unit test for Gemini provider configuration wiring in ChatModelService.
 * Verifies that when the service is instantiated with provider "gemini",
 * the metrics correctly tag the provider name and response success is recorded.
 */
class GeminiProviderConfigTest {

    @Test
    void classify_withGeminiProvider_recordsSuccessMetricWithGeminiTag() throws Exception {
        ChatModel chatModel = mockChatModel();
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        ChatModelService service = serviceWithProvider(chatModel, "gemini", meterRegistry);

        ConversationContextMessage prior = new ConversationContextMessage(1L, 7L, "customer",
                "I need help", OffsetDateTime.parse("2026-08-02T10:00:00Z"));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        when(chatModel.call(promptCaptor.capture()))
                .thenReturn(chatResponse("INFO_REQUEST", "NEUTRAL", "LOW"));

        ClassificationFields result = service.classify("hello", List.of(prior));

        assertThat(result.intent()).isEqualTo("INFO_REQUEST");

        // Verify the metric was recorded with provider=gemini and result=success
        assertThat(meterRegistry.find("classification.latency")
                .tag("provider", "gemini")
                .tag("result", "success")
                .timers())
                .hasSize(1)
                .allSatisfy(timer -> {
                    assertThat(timer.count()).isEqualTo(1);
                    assertThat(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS))
                            .isGreaterThan(0);
                });
    }

    @Test
    void classify_withGeminiProvider_rendersPromptCorrectly() throws Exception {
        ChatModel chatModel = mockChatModel();
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        ChatModelService service = serviceWithProvider(chatModel, "gemini", meterRegistry);

        ConversationContextMessage prior = new ConversationContextMessage(1L, 7L, "customer",
                "I cannot access my account", OffsetDateTime.parse("2026-08-02T10:00:00Z"));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        when(chatModel.call(promptCaptor.capture()))
                .thenReturn(chatResponse("ACCOUNT_ISSUE", "FRUSTRATED", "HIGH"));

        ClassificationFields result = service.classify("still locked out", List.of(prior));

        assertThat(result.intent()).isEqualTo("ACCOUNT_ISSUE");
        assertThat(result.sentiment()).isEqualTo("FRUSTRATED");
        assertThat(result.urgency()).isEqualTo("HIGH");

        String rendered = promptCaptor.getValue().getContents();
        // Verify context placeholder was filled
        assertThat(rendered).contains("I cannot access my account");
        // Verify message placeholder was filled
        assertThat(rendered).contains("still locked out");
    }

    private static ChatModelService serviceWithProvider(ChatModel chatModel, String provider, MeterRegistry meterRegistry) throws IOException {
        LlmPromptProperties promptProperties = new LlmPromptProperties();
        promptProperties.setDefault(loadDefaultPromptTemplate());
        ClassificationMetrics classificationMetrics = new ClassificationMetrics(meterRegistry);
        return new ChatModelService(chatModel, promptProperties, new ConversationContextFormatter(),
                classificationMetrics, provider, Duration.ofSeconds(5));
    }
}
