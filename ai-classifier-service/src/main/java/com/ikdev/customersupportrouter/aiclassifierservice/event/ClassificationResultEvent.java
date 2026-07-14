package com.ikdev.customersupportrouter.aiclassifierservice.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ClassificationResultEvent extends ApplicationEvent {
    private final ClassificationResult classificationResult;

    public ClassificationResultEvent(ClassificationResult classificationResult) {
        super(classificationResult);
        this.classificationResult = classificationResult;
    }
}
