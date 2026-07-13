package com.ikdev.customersupportrouter.aiclassifierservice.model;

public record ClassificationFields(
        String intent,
        String sentiment,
        String urgency) {

    public static final ClassificationFields FALLBACK = new ClassificationFields("UNKNOWN", "NEUTRAL", "UNKNOWN");
}