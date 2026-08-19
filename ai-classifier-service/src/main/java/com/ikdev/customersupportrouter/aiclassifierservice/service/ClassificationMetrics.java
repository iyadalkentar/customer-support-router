package com.ikdev.customersupportrouter.aiclassifierservice.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Metrics for LLM classification in ai-classifier-service.
 *
 * <p>Timer {@code classification.latency}, tagged by:
 * <ul>
 *   <li>{@code provider} — LLM provider name (e.g., {@code gemini}, {@code ollama}, {@code openai})</li>
 *   <li>{@code result} — {@code success}, {@code fallback}, {@code timeout}</li>
 * </ul>
 *
 * <p>The {@code provider} and {@code result} tags vary per call, so the timer is looked up from
 * the registry by name+tags rather than held in a single field (Micrometer bakes tags into a
 * meter at construction; the registry caches the per-tag-combination meter, so repeated calls
 * reuse the same {@code Timer}).
 */
@Component
public class ClassificationMetrics {

    static final String CLASSIFICATION_LATENCY = "classification.latency";

    private final MeterRegistry meterRegistry;

    public ClassificationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records a classification attempt with the given provider, result, and latency.
     *
     * @param provider the LLM provider name (gemini, ollama, openai)
     * @param result the classification result: {@code success}, {@code fallback}, {@code timeout}
     * @param latency the wall-clock duration of the LLM call
     */
    public void recordClassification(String provider, String result, Duration latency) {
        Timer.builder(CLASSIFICATION_LATENCY)
                .tag("provider", provider)
                .tag("result", result)
                .register(meterRegistry)
                .record(latency);
    }
}