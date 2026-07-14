# Phase 3 Status Note — AI Classification Service (in progress)

This note captures what's actually been committed so far in Phase 3. The
[Phase 3 plan note](phase-3-plan-note.md) covers the full set of decisions made
before implementation started — this note tracks progress against that plan
and should be updated again once `ai-classifier-service` itself is scaffolded.

## What's been built so far

- **`Message` entity (`chat-service`) updated** with classification-related
  columns, ready to be populated once `classification-results` events are
  consumed:
  ```java
  @Column(length = 50)
  private String intent;

  @Column(length = 50)
  private String sentiment;

  @Column(length = 50)
  private String urgency;

  @Column(name = "trace_id")
  private UUID traceId;
  ```
  (Presumably paired with a Flyway migration adding these columns, per the
  migration-first workflow established in Phase 1 — worth confirming/logging
  here once the migration file is written, if it hasn't been already.)
- **`traceId` wired into `MessageEvent` and the ingest flow in `chat-service`.**
  Per the plan, this is generated at ingest time and carried through so it can
  be echoed back on the `ClassificationResult` event later, for grep-able
  cross-service log correlation.

## Not yet started

- `ai-classifier-service` module itself — no `pom.xml`, no package structure,
  no code yet. This is the next piece of work.
- `LlmClient` interface / `OllamaLlmClient` implementation.
- Kafka consumer in `ai-classifier-service` (`ai-classifier-group`) on
  `incoming-messages`.
- `classification-results` topic and the `chat-service`-side consumer that
  updates the new `Message` columns.
- Any tests (mocked-`LlmClient` unit tests, or the single real-Ollama smoke
  test) — none of this exists yet since the service doesn't exist yet.

## Open items to revisit when ai-classifier-service scaffolding starts

- Confirm the `Message` migration is committed (schema + entity should match,
  per the `ddl-auto: validate` approach from Phase 1) if it hasn't been
  checked yet.
- All architecture decisions from the plan note (independent module, own event
  DTO copy, no shared types library, no datastore of its own, single-message
  not conversation-aware) still stand — nothing has changed that would revisit
  those.

## Closing leg — Phase 3 closed

The closing leg back into `chat-service` shipped, so the classification
round-trip below is now end-to-end live on the main branch. This section
records what was added and the choices that landed differently from the
original plan note.

### New / edited files

- **New** `ai-classifier-service/.../service/ChatModelService.java` —
  renamed from `OpenAiService` during this session because the LLM-provider
  selection moved from bean-conditional to an
  `LlmProviderEnvironmentPostProcessor` keyed on `llm.provider`, so one
  implementation serves whichever `ChatModel` is active (Ollama or
  OpenAI-compatible).
- **Edited** `ai-classifier-service/.../kafka/MessageEventConsumer.java` —
  wraps `llmClient.classify(...)` in `try`/`catch (Exception)`, publishes
  `ClassificationResult.fallback(...)` (UNKNOWN / NEUTRAL / UNKNOWN) on
  retry exhaustion. This satisfies the plan note's "explicit fallback is
  better than a message silently having no classification" line.
- **New** `chat-service/.../service/ClassificationService.java` — applies a
  `ClassificationResult` to its `Message` row by `messageId`, idempotent on
  redelivery. Missing `messageId` is logged at WARN and dropped; no rethrow,
  no DLQ (DLQ is deferred — see below).
- **New** `chat-service/.../kafka/ClassificationResultConsumer.java` —
  `@KafkaListener(topics = "classification-results", groupId
  = "chat-classification-results-group")`, thin delegator to
  `ClassificationService.applyClassification`.
- **Deleted** `chat-service/.../kafka/MessageEventConsumer.java` — the Phase 2
  stub listener from the placeholder era; consumable now lives in
  `ai-classifier-service` on its own group, this chat-service stub was dead
  code from the moment Phase 3 started writing real code.
- **Edited** `chat-service/src/main/resources/application.yml` —
  `spring.kafka.consumer.group-id` switched from `chat-service-group` (which
  was only the stub) to `chat-classification-results-group`;
  `spring.json.value.default.type` switched from `MessageEvent` to
  `ClassificationResult`. `trusted.packages` already covered both event
  packages.
- **Edited** `chat-service/.../dto/MessageResponse.java` — exposed
  `intent` / `sentiment` / `urgency` on the response DTO. `traceId` was
  deliberately *not* exposed; that's an internal correlation id, and
  surfacing it on the public read-back wasn't on the table.
- **New** `chat-service/src/test/java/.../ClassificationClosingLegIntegrationTest.java` —
  Testcontainers (Postgres + Kafka) integration test. Drives the full path:
  POST `/messages` → publish `ClassificationResult` on the topic with a raw
  `KafkaProducer` matching the production publisher side → assert row fields
  populate via the real `@KafkaListener` → assert the same fields on the
  `MessageResponse` read-back. Three cases: success + read-back,
  idempotency-on-redelivery, unknown-messageId drop.

### Implementation choices that diverged from the plan note (worth flagging)

- **`maxRetries = 1` retry inside `ChatModelService`, plus per-call timeout.**
  The plan called for *"one retry/repair attempt on JSON parse failure, then
  fallback"*. Actual implementation retries on *any* exception (timeout,
  transport, parse) via `@Retryable(value = Exception.class, maxRetries = 1)`
  on the Spring AOP-proxied bean — i.e. extended to the timeout path
  identically. Both failure modes ("no response at all" vs "malformed
  response") land on the same fallback path, which is what the plan wanted
  in spirit but didn't pin down.
- **LLM-call timeout is enforced at the call site, not at the HTTP client.**
  Spring AI's `ChatClient` doesn't expose a per-call timeout, and configuring
  it on the global `RestClient.Builder` would affect *every* HTTP client
  application-wide. The timeout is enforced via
  `Future.get(llmTimeout, TimeUnit.MILLISECONDS)` inside `ChatModelService`,
  backed by a dedicated daemon-thread `ExecutorService` sized to cores (min
  2). Configurable through `llm.timeout` (default `10s`). Side effect: the
  `@Retryable` retry attempt is bounded by the same timeout, so worst-case
  per-message latency is ≈ `2 × llm.timeout + backoff`.
- **`ClassificationResultConsumer` has no `@Retryable`** — the retry tail is
  on the producer side of the topic. If something at the consumer side
  transiently fails (e.g. a brief DB blip), Kafka's consumer-group offset
  tracking handles redelivery on restart, and the `applyClassification`
  method itself is idempotent enough that a repeated delivery is harmless.
- **`traceId` is not re-set on `applyClassification`.** It's established at
  message insert (in `ConversationService.addMessageToConversation`),
  carried on `MessageEvent`, and echoed back on `ClassificationResult` —
  but the consumer skips re-setting it because the value is always identical
  to what's already on the row. Re-setting adds noise and would mask the
  hypothetical future "incoming result's traceId doesn't match the row's"
  bug signal.
- **HTTP actuator requires adding `spring-boot-starter-web` to
  `ai-classifier-service` and pinning `server.port: 8083`** even though the
  classifier module has no public REST API. The Phase 3 plan didn't call
  for a status URL on the classifier; this was added at the end of the
  phase because Phase 6 (Prometheus/Grafana) needs an HTTP actuator
  endpoint to scrape. Pulls in Tomcat on a Kafka-only service — accepted as
  the cost of having a status URL and to mirror chat-service's
  `server.port: 8081` pattern. The actuator's default `/actuator/health`
  exposure is what Phase 6 will pulse; broader endpoint exposure
  (`metrics`, `env`) stays off until then.

### Still deferred (per the original plan note)

- **Routing / escalation logic** — Phase 4, TDD per plan note.
- **Conversation-context classification** — Phase 5, once Redis conversation
  memory exists. `ai-classifier-service` is intentionally not conversation
  stateful in this phase; both services will read from the same Redis store
  once it lands.
- **Dead-letter topic / consumer-level failure handling** beyond the
  in-process fallback path. The current consumer logs at WARN and drops on
  unknown `messageId`; malformed Kafka payloads (deserialization failures)
  fall back to Spring Kafka's default error handling.
- **Idempotency under at-least-once Kafka delivery at the producer side.**
  The plan note flagged that a redelivered incoming-messages event could
  trigger a duplicate LLM call and a duplicate `classification-results`
  event. Current assumption: consumer-side idempotency on `messageId` is
  sufficient (and the closing-leg integration test exercises the path).
  Revisit if duplicate LLM cost becomes an actual concern in production.
- **Full distributed tracing (spanIds, OpenTelemetry, etc.)** — `traceId` is
  correlation-only and grep-able across logs. A real tracing backend would
  be Phase 6 / observability territory and isn't required for log reading
  today.
