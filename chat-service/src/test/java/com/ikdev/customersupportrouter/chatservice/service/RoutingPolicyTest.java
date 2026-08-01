package com.ikdev.customersupportrouter.chatservice.service;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.ikdev.customersupportrouter.chatservice.entity.RoutingDecision;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rule table for the Phase 4 routing decision. Pure JUnit/AssertJ — no Spring.
 *
 * <p>Evaluated top-down, first match wins; values compared case-insensitively.
 * Anything unrecognized (including {@code null} and the LLM's {@code UNKNOWN})
 * simply does not match a rule and falls through to {@code AUTO_RESPOND}.
 */
class RoutingPolicyTest {

    private final RoutingPolicy routingPolicy = new RoutingPolicy();

    static Stream<Arguments> ruleTable() {
        return Stream.of(
                // --- AUTO_RESPOND ---
                Arguments.of("INFO_REQUEST", "NEUTRAL", "LOW", RoutingDecision.AUTO_RESPOND),
                Arguments.of("UNKNOWN", "NEUTRAL", "UNKNOWN", RoutingDecision.AUTO_RESPOND), // LLM fallback
                Arguments.of("INFO_REQUEST", "NEUTRAL", "UNKNOWN", RoutingDecision.AUTO_RESPOND),
                Arguments.of(null, null, null, RoutingDecision.AUTO_RESPOND),
                // --- CREATE_TICKET ---
                Arguments.of("COMPLAINT", "NEUTRAL", "LOW", RoutingDecision.CREATE_TICKET),
                Arguments.of("REQUEST_REFUND", "POSITIVE", "MEDIUM", RoutingDecision.CREATE_TICKET),
                Arguments.of("BUG_REPORT", "POSITIVE", "UNKNOWN", RoutingDecision.CREATE_TICKET),
                Arguments.of("ACCOUNT_ISSUE", "NEUTRAL", "LOW", RoutingDecision.CREATE_TICKET),
                Arguments.of("INFO_REQUEST", "NEUTRAL", "HIGH", RoutingDecision.CREATE_TICKET),
                Arguments.of("INFO_REQUEST", "POSITIVE", "HIGH", RoutingDecision.CREATE_TICKET),
                Arguments.of("INFO_REQUEST", "NEGATIVE", "LOW", RoutingDecision.CREATE_TICKET),
                Arguments.of("INFO_REQUEST", "NEGATIVE", "MEDIUM", RoutingDecision.CREATE_TICKET),
                Arguments.of("INFO_REQUEST", "NEGATIVE", "UNKNOWN", RoutingDecision.CREATE_TICKET),
                Arguments.of("UNKNOWN", "NEGATIVE", "UNKNOWN", RoutingDecision.CREATE_TICKET), // partial fallback
                Arguments.of(null, "NEGATIVE", null, RoutingDecision.CREATE_TICKET),
                // --- ESCALATE_TO_HUMAN ---
                Arguments.of("INFO_REQUEST", "NEGATIVE", "HIGH", RoutingDecision.ESCALATE_TO_HUMAN),
                Arguments.of("COMPLAINT", "NEGATIVE", "HIGH", RoutingDecision.ESCALATE_TO_HUMAN), // rule 1 beats rule 2
                Arguments.of("UNKNOWN", "NEGATIVE", "HIGH", RoutingDecision.ESCALATE_TO_HUMAN),
                // case-insensitive normalization
                Arguments.of("info_request", "negative", "high", RoutingDecision.ESCALATE_TO_HUMAN),
                Arguments.of("Complaint", "NEUTRAL", "low", RoutingDecision.CREATE_TICKET));
    }

    @ParameterizedTest(name = "decide({0}, {1}, {2}) -> {3}")
    @MethodSource("ruleTable")
    void decide_matchesRuleTable(String intent, String sentiment, String urgency, RoutingDecision expected) {
        assertThat(routingPolicy.decide(intent, sentiment, urgency)).isEqualTo(expected);
    }

    @Test
    void normalize_ignoresSurroundingWhitespace() {
        assertThat(routingPolicy.decide("  INFO_REQUEST  ", "  NEGATIVE  ", " HIGH ")).isEqualTo(RoutingDecision.ESCALATE_TO_HUMAN);
    }

    @Test
    void unknownNonFallbackValues_doNotMatchRules() {
        // An intent the LLM invents that isn't in the allowlist must not be
        // treated as an escalation intent.
        assertThat(routingPolicy.decide("HOW_TO", "POSITIVE", "LOW")).isEqualTo(RoutingDecision.AUTO_RESPOND);
    }
}
