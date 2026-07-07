# Phase 3 Plan Note — AI Classification Service (pre-implementation)

This note captures decisions made during planning discussion, before any code was
written. Intended to be dropped into project knowledge so the Phase 3 build
conversation doesn't need to re-derive these calls.

## Scope for Phase 3
Classification only. Routing/escalation logic is explicitly **not** part of this
phase (see "Deferred to later phases" below).

## Architecture decisions

- **`ai-classifier-service` is fully independent** from `chat-service` — separate
  Spring Boot module, own `pom.xml`, no shared types library. Services communicate
  only via Kafka. Accepted cost: each service defines its own local copy of the
  event DTO shape (minor duplication, in exchange for real decoupling).
- **Consumer lives in `ai-classifier-service`**, not `chat-service`. It consumes
  `incoming-messages` directly with its own consumer group (`ai-classifier-group`),
  rather than `chat-service` relaying via REST.
- **Classification is single-message, not conversation-aware.** No conversation
  history is read or stored inside `ai-classifier-service`. Conversation context is
  explicitly Phase 5 work (Redis-backed), so it isn't pulled forward here. Rationale:
  keeps the classifier stateless and testable now, and avoids creating a second
  source of truth for conversation state outside Postgres — when Phase 5 lands, both
  `chat-service` and `ai-classifier-service` should read the same Redis store rather
  than the classifier maintaining its own.
- **`ai-classifier-service` has no datastore of its own** in this phase — no
  Postgres, no Redis. It's a pure read-event → call-LLM → publish-result service.
- **Results flow back via a new Kafka topic**, `classification-results`, keyed by
  `conversationId` (same per-conversation ordering rationale as `incoming-messages`).
  `chat-service` adds a new consumer for this topic and updates classification
  columns on its existing tables.
- **Fallback classifications are still published.** If the LLM call times out or
  JSON parsing fails after one retry, the service falls back to `UNKNOWN`/neutral
  and publishes that result anyway — an explicit fallback is better than a message
  silently having no classification at all.

## Event schema additions

- **`traceId`** (String, UUID) added to `MessageEvent` in `chat-service`, generated
  at ingest time. Carried through into `ai-classifier-service`'s local event DTO and
  echoed back on the `ClassificationResult` event it publishes. Purpose: grep-able
  correlation across service logs for one message's journey, without adopting full
  distributed tracing (OpenTelemetry/spans) yet — that's deferred, see below.
- `ClassificationResult` (published to `classification-results`) should carry:
  `messageId`, `conversationId`, `traceId`, `eventVersion`, plus the classification
  fields (intent, sentiment, urgency). Mirrors the shape of `MessageEvent` from
  Phase 2 rather than inventing a different convention.

## LLM client design

- `LlmClient` interface (per original project plan), `OllamaLlmClient` implementation.
- **Explicit timeout** on the LLM call itself, separate from JSON parse/repair retry
  logic — these are two different failure modes (no response at all, vs. malformed
  response) and both should route to the same fallback path.
- One retry/repair attempt on JSON parse failure, then fallback to `UNKNOWN`/neutral
  classification (per original project plan's reliability note).

## Testing strategy

- Unit tests against a **mocked `LlmClient`** for prompt-building, JSON parsing,
  retry, and fallback logic — deterministic, fast.
- At most **one real-Ollama smoke test**, not a full suite against live model output,
  since local LLM responses are slow and non-deterministic.

## Deferred to later phases (explicitly, not forgotten)

- **Routing/escalation logic** — Phase 4, as originally planned. To be built
  **test-first (TDD)**, since it's pure business logic with no infra dependencies
  (per Phase 2 note's reasoning).
- **Conversation-context classification** — Phase 5, once Redis conversation memory
  exists. `ai-classifier-service` will read from the same Redis store `chat-service`
  writes to, rather than maintaining independent state.
- **Dead-letter topic / consumer-level failure handling** beyond the LLM
  timeout-and-fallback path — not built in Phase 3. Revisit if failure modes beyond
  "LLM didn't respond in time" show up (e.g. malformed/unparseable Kafka event
  itself reaching the consumer).
- **Idempotency under at-least-once Kafka delivery** — a redelivered message could
  cause a duplicate LLM call and a duplicate `classification-results` event for the
  same `messageId`. Current assumption is that this is acceptable because
  `chat-service` can upsert on `messageId`, but this hasn't been stress-tested or
  finalized — revisit if it becomes a real issue.
- **Full distributed tracing (spanId, OpenTelemetry, etc.)** — only `traceId` is
  being added now, deliberately without a per-service `spanId`. A single
  correlation ID is enough for manual log grepping today; a real tracing backend
  (Phase 6 observability territory, or beyond) would be needed before per-service
  span IDs would pay for themselves.
