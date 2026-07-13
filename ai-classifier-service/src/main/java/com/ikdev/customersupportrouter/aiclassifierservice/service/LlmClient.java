package com.ikdev.customersupportrouter.aiclassifierservice.service;

import com.ikdev.customersupportrouter.aiclassifierservice.model.ClassificationResult;

public interface LlmClient {
    ClassificationResult classify(String prompt);
}
