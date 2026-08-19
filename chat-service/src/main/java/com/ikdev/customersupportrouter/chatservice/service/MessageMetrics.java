package com.ikdev.customersupportrouter.chatservice.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Metrics for message ingestion in chat-service.
 *
 * <p>Counter {@code messages.processed}, tagged by:
 * <ul>
 *   <li>{@code service} — always {@code chat-service} here</li>
 *   <li>{@code outcome} — {@code success}, {@code rejected} (expected client-caused
 *       rejection: 400/404/409), or {@code error} (unexpected server-side failure).
 *       Kept distinct from {@code rejected} so an error-rate dashboard panel isn't
 *       polluted by routine client rejections.</li>
 * </ul>
 *
 * <p>The {@code outcome} tag varies per call, so the counter is looked up from the
 * registry by name+tags rather than held in a single field (Micrometer bakes tags
 * into a meter at construction; the registry caches the per-tag-combination meter,
 * so repeated calls reuse the same {@code Counter}).
 */
@Component
public class MessageMetrics {

    static final String MESSAGES_PROCESSED = "messages.processed";
    static final String SERVICE_TAG_VALUE = "chat-service";

    private final MeterRegistry meterRegistry;

    public MessageMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records a processed message with the given outcome.
     * Valid outcomes: {@code success}, {@code rejected}, {@code error}.
     */
    public void recordIngest(String outcome) {
        meterRegistry.counter(MESSAGES_PROCESSED,
                "service", SERVICE_TAG_VALUE,
                "outcome", outcome)
                .increment();
    }
}
