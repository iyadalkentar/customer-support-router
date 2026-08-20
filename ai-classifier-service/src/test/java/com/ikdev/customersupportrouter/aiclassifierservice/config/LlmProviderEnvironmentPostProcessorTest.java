package com.ikdev.customersupportrouter.aiclassifierservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link LlmProviderEnvironmentPostProcessor}.
 */
class LlmProviderEnvironmentPostProcessorTest {

    private final LlmProviderEnvironmentPostProcessor postProcessor = new LlmProviderEnvironmentPostProcessor();

    @Test
    void postProcessEnvironment_openaiProvider_setsChatModelToOpenai() {
        MockEnvironment environment = new MockEnvironment().withProperty("llm.provider", "openai");

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("openai");
    }

    @Test
    void postProcessEnvironment_ollamaProvider_setsChatModelToOllama() {
        MockEnvironment environment = new MockEnvironment().withProperty("llm.provider", "ollama");

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("ollama");
    }

    @Test
    void postProcessEnvironment_geminiProviderWithApiKey_setsChatModelToGoogleGenai() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("llm.provider", "gemini")
                .withProperty("GEMINI_API_KEY", "test-key");

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("google-genai");
    }

    @Test
    void postProcessEnvironment_geminiProviderWithoutApiKey_throwsIllegalStateException() {
        MockEnvironment environment = new MockEnvironment().withProperty("llm.provider", "gemini");

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    void postProcessEnvironment_unsupportedProvider_throwsIllegalStateException() {
        MockEnvironment environment = new MockEnvironment().withProperty("llm.provider", "unsupported-value");

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ollama")
                .hasMessageContaining("openai")
                .hasMessageContaining("gemini");
    }

    @Test
    void postProcessEnvironment_providerUnset_doesNotAddProperty() {
        MockEnvironment environment = new MockEnvironment();

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.ai.model.chat")).isNull();
    }

    @Test
    void postProcessEnvironment_providerBlank_doesNotAddProperty() {
        MockEnvironment environment = new MockEnvironment().withProperty("llm.provider", "   ");

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.ai.model.chat")).isNull();
    }
}
