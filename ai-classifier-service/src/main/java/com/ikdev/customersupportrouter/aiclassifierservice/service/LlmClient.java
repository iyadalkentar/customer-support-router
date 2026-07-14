package com.ikdev.customersupportrouter.aiclassifierservice.service;

import com.ikdev.customersupportrouter.aiclassifierservice.event.ClassificationFields;

public interface LlmClient {
    ClassificationFields classify(String prompt);
}
