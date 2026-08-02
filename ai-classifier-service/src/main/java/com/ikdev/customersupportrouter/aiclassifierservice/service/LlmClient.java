package com.ikdev.customersupportrouter.aiclassifierservice.service;

import java.util.List;

import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationFields;
import com.ikdev.customersupportrouter.aiclassifierservice.memory.ConversationContextMessage;

public interface LlmClient {
    ClassificationFields classify(String content, List<ConversationContextMessage> context);
}
