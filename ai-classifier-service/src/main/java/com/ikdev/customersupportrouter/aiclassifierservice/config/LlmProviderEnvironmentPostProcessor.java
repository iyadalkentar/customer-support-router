package com.ikdev.customersupportrouter.aiclassifierservice.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

public class LlmProviderEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String provider = environment.getProperty("llm.provider");

        if (provider == null || provider.isBlank()) {
            return;
        }

        switch (provider.toLowerCase()) {
            case "openai", "ollama" ->
                    environment.getPropertySources().addFirst(
                            new MapPropertySource(
                                    "llm-provider",
                                    Map.of("spring.ai.model.chat", provider)));
            default ->
                    throw new IllegalStateException(
                            "Unsupported llm.provider: " + provider);
        }

    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
