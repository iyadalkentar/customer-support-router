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
