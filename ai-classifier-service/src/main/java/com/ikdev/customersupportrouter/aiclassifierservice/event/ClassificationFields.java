package com.ikdev.customersupportrouter.aiclassifierservice.event;

public record ClassificationFields(
        String intent,
        String sentiment,
        String urgency) {

    public static final ClassificationFields FALLBACK = new ClassificationFields("UNKNOWN", "NEUTRAL", "UNKNOWN");
}