# Phase 4 Status Note — Routing / Escalation Logic (complete)

This note records what shipped in Phase 4, the key decisions, the routing rule
table, and items deferred to later phases. Phase 4 was built **test-first (TDD)**
per the Phase 2 note's intent — the pure decision function and the service
behavior contracts were written as tests before implementation, and each unit
was run RED → GREEN before moving on.

## What was built

- **`RoutingDecision` enum** (`entity/RoutingDecision.java`) — `AUTO_RESPOND`,
  `ESCALATE_TO_HUMAN`, `CREATE_TICKET`, with `isEscalation()`.
- **`routing_decision` column on `messages`** (`V3__add_routing_decision.sql`,
  entity field, and `MessageResponse` exposure) — the decision is recorded and
  readable via `GET /conversations/{id}/messages`, mirroring how the
  classification fields were exposed in Phase 3.
- **`RoutingPolicy`** (`service/RoutingPolicy.java`) — a *pure* decision
  function (no infra deps) mapping (intent, sentiment, urgency) → decision.
  22 rule-table tests; the only component in the phase with no Spring
  dependency, deliberately, so the table is unit-tested in isolation.
- **`RoutingService`** (`service/RoutingService.java`) — orchestrates
  apply-classification → decide → record → escalate, all inside **one
  `@Transactional` boundary** so classification fields, `routing_decision`, and
  the ticket row are atomic.
- **`EscalationService`** (`service/EscalationService.java`) — action executor:
  finds-or-creates a single OPEN ticket per conversation and fires an
  `EscalationPersistedEvent`; `deescalate` closes the OPEN ticket once no
  message in the conversation still escalates.
- **`EscalationEvent` + `EscalationPersistedEvent` + `EscalationEventPublisher`**
  — mirrors the Phase 2 `MessageEventPublisher` outbox: the publisher listens
  via `@TransactionalEventListener(phase = AFTER_COMMIT)` and sends the event to
  the new **`escalations`** topic keyed by `conversationId`.
- **`TicketRepository`** — `findFirstByConversationIdAndStatus` (escalation
  lookup) and `findByConversationId` (read paths/tests).
- **`MessageRepository.existsByConversationIdAndRoutingDecisionInAndIdNot`** —
  the de-escalation guard: are any OTHER messages in the conversation still
  escalated?
- **Wiring** — `ClassificationResultConsumer` now delegates to
  `RoutingService.applyClassificationAndRoute`; `ClassificationService.
  applyClassification` returns `Optional<Message>` so the orchestrator doesn't
  re-fetch the just-applied message.

## Routing rule table (`RoutingPolicy`, first match wins, case-insensitive)

| # | Condition | Decision |
|---|---|---|
| 1 | sentiment `NEGATIVE` AND urgency `HIGH` | `ESCALATE_TO_HUMAN` |
| 2 | intent ∈ {`COMPLAINT`, `REQUEST_REFUND`, `BUG_REPORT`, `ACCOUNT_ISSUE`} | `CREATE_TICKET` |
| 3 | urgency `HIGH` | `CREATE_TICKET` |
| 4 | sentiment `NEGATIVE` | `CREATE_TICKET` |
| 5 | otherwise (incl. LLM fallback `UNKNOWN/NEUTRAL/UNKNOWN`, nulls) | `AUTO_RESPOND` |

Safety property: a full LLM failure (`UNKNOWN/NEUTRAL/UNKNOWN`) never escalates
or tickets; strong partial signals still route (`UNKNOWN/NEGATIVE/HIGH` →
rule 1). The escalation-intent set is a curated allowlist — the NEGATIVE/HIGH
rules are the safety net for intents the LLM invents outside it.

## Key decisions / deviations from the original plan

- **Both escalation outcomes behave identically this phase** — `ESCALATE_TO_HUMAN`
  and `CREATE_TICKET` each create/reuse an OPEN ticket and publish an escalation
  event. The distinction is preserved in the stored `routing_decision` and in
  `EscalationEvent.routingDecision` so a later phase can differentiate
  (assign an owner, page a human, set ticket priority).
- **Routing is synchronous within the classification transaction** rather than a
  separate Spring decision event — this keeps `routing_decision` + ticket
  atomic with the classification write. Only the **Kafka publish** is deferred
  to AFTER_COMMIT (the part that earns the outbox pattern).
- **Idempotency on at-least-once redelivery**: an escalation fires only when the
  stored decision differs from the newly derived one. Re-applying the same
  `ClassificationResult` neither re-creates a ticket nor re-publishes an event
  (proved by `redelivery_sameResult_doesNotDuplicateTicketOrEscalationEvent`).
- **De-escalation is handled symmetrically**: a corrected classification moving a
  message from ESCALATE_TO_HUMAN/CREATE_TICKET back to AUTO_RESPOND asks
  `EscalationService.deescalate` to close the conversation's OPEN ticket — but
  only when `existsByConversationIdAndRoutingDecisionInAndIdNot` finds no other
  escalated message, so a sibling's still-open escalation is never clobbered.
- **Partial unique index** `uq_tickets_one_open_per_conversation
  ON tickets(conversation_id) WHERE status = 'OPEN'` enforces the
  one-OPEN-ticket invariant at the schema level. Safe to add (tickets table was
  empty). Drop it if >1 OPEN ticket per conversation is ever legitimate.
- **No `escalations` consumer in chat-service this phase** — a future
  human-support service owns that topic.
- No `application.yml` change: `escalations` is auto-created like the existing
  topics; chat-service only *produces* to it, so the global consumer default
  type (`ClassificationResult`) is unaffected.

## Tests

- `RoutingPolicyTest` — 22 cases (rule table + fallback/null/case-insensitive).
- `EscalationServiceTest` — 6 cases (create new ticket, reuse existing OPEN
  ticket without save, full event payload, close on de-escalation when no
  sibling escalates, keep open when a sibling still escalates, no-open-ticket
  no-op).
- `RoutingServiceTest` — 6 cases (drop unknown, auto-respond records, new
  decision escalates once, **redelivery same decision skips**, decision change
  fires once, **de-escalation calls `deescalate`**).
- `EscalationEventPublisherTest` — 2 cases (topic + conversationId key;
  regression guard that the key is not the ticket id).
- `RoutingEscalationIntegrationTest` — 5 end-to-end cases (Testcontainers
  Postgres + Kafka, real `@KafkaListener`): negative+high creates ticket +
  publishes event + exposes `routingDecision`; redelivery produces no duplicate
  ticket/event; auto-respond records without ticket/event; LLM fallback routes
  to auto-respond; **corrected classification de-escalates and closes the
  ticket**.
- Full `chat-service` suite: **57 tests green** (requires the compose infra —
  `docker compose up -d postgres kafka` — for the `ChatServiceApplicationTests`
  context-load smoke test; the Testcontainers integration tests self-provision).
  `ai-classifier-service`: untouched, 1 test green.

## Open items / deferred

- **Differentiate the escalation outcomes** — currently identical; later phase
  can use `EscalationEvent.routingDecision` to assign owners / set priority.
- **Auto-respond reply generation** — `AUTO_RESPOND` currently records the
  decision only. Generating a reply needs Phase 5 (Redis conversation memory)
  and its own LLM call.
- **`escalations` topic consumer** — a human-support service to consume the
  events (out of scope here).
- **Transactional outbox table** — the AFTER_COMMIT send has a small
  failure window (event lost if the Kafka send throws after commit; redelivery
  skips it because the stored decision is unchanged). Same exposure as the
  Phase 2 publisher; a real outbox table is the proper fix, deferred. As an
  interim, both publishers now log an ERROR with the exact ids when the send
  fails, so a loss is visible and manually republishable.
- **De-escalation retraction event** — closing a ticket is recorded in Postgres
  and logged, but no retraction is published on `escalations` (deferred with the
  future human-support consumer).
- **`spring.json.value.default.type` is a single global consumer default** — if
  chat-service ever adds a listener on `escalations`, it needs per-listener
  deserializer config or type headers.
