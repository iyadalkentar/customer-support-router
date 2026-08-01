package com.ikdev.customersupportrouter.chatservice.service;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ikdev.customersupportrouter.chatservice.entity.RoutingDecision;

/**
 * Pure routing decision function (Phase 4): maps a message's classification
 * (intent, sentiment, urgency) to a {@link RoutingDecision}.
 *
 * <p>No infrastructure dependencies — intentionally a plain component so the
 * decision table is unit-testable in isolation (test-first). Rules are
 * evaluated top-down, first match wins; values are normalized to
 * {@link Locale#ROOT} uppercase and trimmed before comparison, so anything
 * unrecognized — including {@code null} and the LLM's {@code UNKNOWN} — simply
 * does not match and falls through to {@code AUTO_RESPOND}.
 *
 * <p>Safety property: a full LLM fallback (UNKNOWN / NEUTRAL / UNKNOWN) never
 * escalates or tickets. Strong partial signals still route (e.g. NEGATIVE
 * sentiment alone tickets, even with UNKNOWN intent).
 */
@Component
public class RoutingPolicy {

    private static final Set<String> ESCALATION_INTENTS =
            Set.of("COMPLAINT", "REQUEST_REFUND", "BUG_REPORT", "ACCOUNT_ISSUE");

    public RoutingDecision decide(String intent, String sentiment, String urgency) {
        String i = normalize(intent);
        String s = normalize(sentiment);
        String u = normalize(urgency);

        boolean negative = "NEGATIVE".equals(s);
        boolean high = "HIGH".equals(u);

        // Rule 1: canonical escalation — negative + high.
        if (negative && high) {
            return RoutingDecision.ESCALATE_TO_HUMAN;
        }
        // Rule 2: escalation intents (curated allowlist over free-text LLM intents).
        if (i != null && ESCALATION_INTENTS.contains(i)) {
            return RoutingDecision.CREATE_TICKET;
        }
        // Rule 3: high urgency alone tickets.
        if (high) {
            return RoutingDecision.CREATE_TICKET;
        }
        // Rule 4: negative sentiment alone tickets.
        if (negative) {
            return RoutingDecision.CREATE_TICKET;
        }
        // Rule 5: everything else auto-responds (incl. LLM fallback).
        return RoutingDecision.AUTO_RESPOND;
    }

    private static String normalize(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT).trim();
    }
}
