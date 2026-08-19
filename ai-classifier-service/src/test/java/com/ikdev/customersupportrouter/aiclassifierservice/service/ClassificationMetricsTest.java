package com.ikdev.customersupportrouter.aiclassifierservice.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link ClassificationMetrics}.
 */
class ClassificationMetricsTest {

    @Test
    void recordClassification_recordsTimerWithProviderAndResultTags() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        ClassificationMetrics metrics = new ClassificationMetrics(meterRegistry);

        metrics.recordClassification("gemini", "success", Duration.ofMillis(150));

        Timer timer = meterRegistry.get("classification.latency")
                .tag("provider", "gemini")
                .tag("result", "success")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(140);
    }

    @Test
    void recordClassification_withMultipleCalls_accumulatesCount() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        ClassificationMetrics metrics = new ClassificationMetrics(meterRegistry);

        metrics.recordClassification("ollama", "success", Duration.ofMillis(100));
        metrics.recordClassification("ollama", "success", Duration.ofMillis(200));
        metrics.recordClassification("ollama", "fallback", Duration.ofMillis(50));

        Timer successTimer = meterRegistry.get("classification.latency")
                .tag("provider", "ollama")
                .tag("result", "success")
                .timer();
        Timer fallbackTimer = meterRegistry.get("classification.latency")
                .tag("provider", "ollama")
                .tag("result", "fallback")
                .timer();

        assertThat(successTimer.count()).isEqualTo(2);
        assertThat(fallbackTimer.count()).isEqualTo(1);
    }

    @Test
    void recordClassification_differentProviders_createSeparateMeters() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        ClassificationMetrics metrics = new ClassificationMetrics(meterRegistry);

        metrics.recordClassification("gemini", "success", Duration.ofMillis(100));
        metrics.recordClassification("openai", "success", Duration.ofMillis(100));
        metrics.recordClassification("ollama", "success", Duration.ofMillis(100));

        assertThat(meterRegistry.get("classification.latency").tag("provider", "gemini").tag("result", "success").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("classification.latency").tag("provider", "openai").tag("result", "success").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("classification.latency").tag("provider", "ollama").tag("result", "success").timer().count()).isEqualTo(1);
    }

    @Test
    void recordClassification_allResultValues() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        ClassificationMetrics metrics = new ClassificationMetrics(meterRegistry);

        metrics.recordClassification("gemini", "success", Duration.ofMillis(100));
        metrics.recordClassification("gemini", "fallback", Duration.ofMillis(100));
        metrics.recordClassification("gemini", "timeout", Duration.ofMillis(100));

        assertThat(meterRegistry.get("classification.latency").tag("provider", "gemini").tag("result", "success").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("classification.latency").tag("provider", "gemini").tag("result", "fallback").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("classification.latency").tag("provider", "gemini").tag("result", "timeout").timer().count()).isEqualTo(1);
    }
}