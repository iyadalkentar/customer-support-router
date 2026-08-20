package com.ikdev.customersupportrouter.chatservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link MessageMetrics}.
 */
class MessageMetricsTest {

    @Test
    void recordIngest_success_incrementsCounterWithCorrectTags() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        MessageMetrics metrics = new MessageMetrics(meterRegistry);

        metrics.recordIngest("success");

        Counter counter = meterRegistry.get("messages.processed")
                .tag("service", "chat-service")
                .tag("outcome", "success")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void recordIngest_rejectedAndErrorAreSeparateCounters() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        MessageMetrics metrics = new MessageMetrics(meterRegistry);

        metrics.recordIngest("rejected");
        metrics.recordIngest("error");

        Counter rejectedCounter = meterRegistry.get("messages.processed")
                .tag("service", "chat-service")
                .tag("outcome", "rejected")
                .counter();
        Counter errorCounter = meterRegistry.get("messages.processed")
                .tag("service", "chat-service")
                .tag("outcome", "error")
                .counter();

        assertThat(rejectedCounter.count()).isEqualTo(1);
        assertThat(errorCounter.count()).isEqualTo(1);
    }

    @Test
    void recordIngest_multipleCalls_accumulatesCount() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        MessageMetrics metrics = new MessageMetrics(meterRegistry);

        metrics.recordIngest("success");
        metrics.recordIngest("success");
        metrics.recordIngest("success");

        Counter counter = meterRegistry.get("messages.processed")
                .tag("service", "chat-service")
                .tag("outcome", "success")
                .counter();

        assertThat(counter.count()).isEqualTo(3);
    }

    @Test
    void recordIngest_allOutcomeValues_serviceTagAlwaysChatService() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        MessageMetrics metrics = new MessageMetrics(meterRegistry);

        metrics.recordIngest("success");
        metrics.recordIngest("rejected");
        metrics.recordIngest("error");

        Counter successCounter = meterRegistry.get("messages.processed")
                .tag("service", "chat-service")
                .tag("outcome", "success")
                .counter();
        Counter rejectedCounter = meterRegistry.get("messages.processed")
                .tag("service", "chat-service")
                .tag("outcome", "rejected")
                .counter();
        Counter errorCounter = meterRegistry.get("messages.processed")
                .tag("service", "chat-service")
                .tag("outcome", "error")
                .counter();

        assertThat(successCounter.count()).isEqualTo(1);
        assertThat(rejectedCounter.count()).isEqualTo(1);
        assertThat(errorCounter.count()).isEqualTo(1);
    }
}
