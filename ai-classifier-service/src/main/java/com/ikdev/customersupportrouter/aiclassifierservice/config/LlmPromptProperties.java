package com.ikdev.customersupportrouter.aiclassifierservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.Objects;

@ConfigurationProperties(prefix = "llm.prompt")
@Getter
public class LlmPromptProperties{
    String defaultPrompt;
    @Setter
    Map<String, String> models;
    public String resolve(String model) {
        var def = Objects.requireNonNullElse(defaultPrompt, "");
        return models == null ? def : models.getOrDefault(model, def);
    }
    public void setDefault(String defaultPrompt){
        this.defaultPrompt = defaultPrompt;
    }
}
