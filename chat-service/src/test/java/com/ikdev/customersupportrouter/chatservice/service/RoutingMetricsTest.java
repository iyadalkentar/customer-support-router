package com.ikdev.customersupportrouter.chatservice.service;

import com.ikdev.customersupportrouter.chatservice.entity.RoutingDecision;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link RoutingMetrics}.
 */
class RoutingMetricsTest {

    @Test
    void recordEscalation_escalateToHuman_incrementsCounterWithCorrectTags() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        RoutingMetrics metrics = new RoutingMetrics(meterRegistry);

        metrics.recordEscalation(RoutingDecision.ESCALATE_TO_HUMAN);

        Counter counter = meterRegistry.get("escalation.event")
                .tag("routing_decision", "ESCALATE_TO_HUMAN")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void recordEscalation_createTicket_incrementsSeparateCounter() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        RoutingMetrics metrics = new RoutingMetrics(meterRegistry);

        metrics.recordEscalation(RoutingDecision.CREATE_TICKET);

        Counter counter = meterRegistry.get("escalation.event")
                .tag("routing_decision", "CREATE_TICKET")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void recordEscalation_escalateToHumanAndCreateTicket_tracksEachSeparately() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        RoutingMetrics metrics = new RoutingMetrics(meterRegistry);

        metrics.recordEscalation(RoutingDecision.ESCALATE_TO_HUMAN);
        metrics.recordEscalation(RoutingDecision.CREATE_TICKET);

        Counter escalateCounter = meterRegistry.get("escalation.event")
                .tag("routing_decision", "ESCALATE_TO_HUMAN")
                .counter();
        Counter ticketCounter = meterRegistry.get("escalation.event")
                .tag("routing_decision", "CREATE_TICKET")
                .counter();

        assertThat(escalateCounter.count()).isEqualTo(1);
        assertThat(ticketCounter.count()).isEqualTo(1);
    }

    @Test
    void recordEscalation_autoRespond_doesNotIncrementCounter() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        RoutingMetrics metrics = new RoutingMetrics(meterRegistry);

        metrics.recordEscalation(RoutingDecision.AUTO_RESPOND);

        // AUTO_RESPOND is not an escalation, so no meter should exist
        // Try to find any meter with escalation.event name — should be empty
        assertThat(meterRegistry.find("escalation.event").counters()).isEmpty();
    }

    @Test
    void recordEscalation_null_doesNotThrowAndDoesNotIncrementCounter() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        RoutingMetrics metrics = new RoutingMetrics(meterRegistry);

        // Should not throw
        metrics.recordEscalation(null);

        // No meter should have been created
        assertThat(meterRegistry.find("escalation.event").counters()).isEmpty();
    }

    @Test
    void recordEscalation_multipleCalls_accumulatesCount() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        RoutingMetrics metrics = new RoutingMetrics(meterRegistry);

        metrics.recordEscalation(RoutingDecision.ESCALATE_TO_HUMAN);
        metrics.recordEscalation(RoutingDecision.ESCALATE_TO_HUMAN);
        metrics.recordEscalation(RoutingDecision.CREATE_TICKET);
        metrics.recordEscalation(RoutingDecision.CREATE_TICKET);
        metrics.recordEscalation(RoutingDecision.CREATE_TICKET);

        Counter escalateCounter = meterRegistry.get("escalation.event")
                .tag("routing_decision", "ESCALATE_TO_HUMAN")
                .counter();
        Counter ticketCounter = meterRegistry.get("escalation.event")
                .tag("routing_decision", "CREATE_TICKET")
                .counter();

        assertThat(escalateCounter.count()).isEqualTo(2);
        assertThat(ticketCounter.count()).isEqualTo(3);
    }
}
