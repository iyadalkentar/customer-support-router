# Phase 5 Status Note — Redis Conversation Memory (complete)

This note records what shipped in Phase 5, the key decisions, the tests, and
items deferred to later phases. Phase 5 was built point-by-point with the user
in the loop; the living plan [`phase-5-plan.md`](phase-5-plan.md) tracks the
exact sequence.

## What was built

- **Redis infrastructure** — `redis:8.10.0-alpine` service added to
  `docker-compose.yml` (port 6379, `redis_data` volume, `redis-cli ping`
  healthcheck). Compose command is now `docker compose up -d postgres kafka redis`.
- **chat-service write-through cache** (new `memory/` package):
  - **`ConversationMemoryWriter`** — `@TransactionalEventListener(AFTER_COMMIT)`
    on `MessagePersistedEvent` (mirrors `MessageEventPublisher`). Key scheme
    `conversation:{conversationId}:messages` (Redis LIST; left = oldest). On a
    key hit: `rightPush` → `expire` (TTL refresh) → `trim` to last N. On a miss:
    backfills the last N from Postgres and stops (the just-committed message is
    already the newest element of the query — re-appending would duplicate it).
    Any Redis failure is caught + logged, never thrown out of AFTER_COMMIT.
  - **`ConversationMemoryEntry`** — 5-field wire record (messageId,
    conversationId, sender, content, createdAt); the **cross-service contract**.
  - **`ConversationMemoryProperties`** — `conversation.memory.*` (`size=10`,
    `ttl=24h`, `enabled=true`). `@ConfigurationPropertiesScan` added to
    `ChatServiceApplication`.
- **ai-classifier-service reader + prompt** (new `memory/` package):
  - **`ConversationMemoryReader`** — `LRANGE` 0..size-1, skips unparseable
    entries, returns oldest→newest; any Redis failure degrades to an empty list.
  - **`ConversationContextFormatter`** — numbered `[sender] content` lines;
    empty → `"No prior conversation messages."`.
  - **`LlmClient.classify(content, context)`** — `ChatModelService` injects the
    rendered context via `.param("context", ...)`; the prompt template gains a
    "Prior conversation context (oldest first): `{context}`" section.
  - **`MessageEventConsumer`** filters out the current `messageId` so context is
    **strictly prior turns** (removes writer/reader timing nondeterminism).
- **Config** — `spring.data.redis` on both services with 2s timeouts (Lettuce's
  default is 60s; a dead Redis must not stall the AFTER_COMMIT callback), plus
  `conversation.memory.*`.

## Key decisions / deviations from the plan

- **chat-service is the single Redis writer** (write-through on ingest); the
  classifier only reads. Postgres stays the source of truth; Redis is a
  fast-access layer that must never break ingest or classification. One writer,
  aligned with the source of truth.
- **Backfill-on-miss instead of always-rewrite** — a fresh/evicted Redis list is
  rebuilt from Postgres once, then maintained by append + trim.
- **Current-message filter in the consumer** — the classifier never sees the very
  message it is classifying in its context, regardless of whether the writer's
  append landed before the reader ran.
- **Redis payload is JSON via the Boot Jackson 3 `ObjectMapper`** — no Jackson 2,
  consistent with the project's Jackson-3-only rule. `OffsetDateTime` serializes
  ISO-8601.
- **No reply generation** — `AUTO_RESPOND` still records the decision only;
  generating replies stays deferred (needs its own LLM call; out of scope for
  this phase).
- **No Flyway migration** — Redis is schema-less; keys auto-create.

## Tests

- **chat-service** (suite: **65 green**):
  - `ConversationMemoryWriterTest` (4): append/trim/TTL, backfill-no-duplicate,
    Redis-down no-throw, disabled no-op.
  - `ConversationMemoryIntegrationTest` (3, Postgres+Kafka+Redis Testcontainers):
    single entry + TTL, append oldest-first, trim to `size=3`.
  - `ConversationMemoryRedisDownIntegrationTest` (1): ingest 202 + Postgres
    read-back with no Redis available.
- **ai-classifier-service** (suite: **11 green**):
  - `ConversationContextFormatterTest` (3): empty/null placeholder, single,
    multiple oldest-first.
  - `ConversationMemoryReaderTest` (5): parse in order, empty, Redis-down empty,
    garbage skip, disabled no-op.
  - `ConversationMemoryReaderIntegrationTest` (2): parses a chat-service-shaped
    fixture in order via the shared key scheme; missing key → empty. Locks the
    cross-service wire contract.
- **Manual e2e**: Redis `MONITOR` captured the writer's `RPUSH
  conversation:{id}:messages` and the reader's `LRANGE conversation:{id}:messages`
  for the same conversation; read-back showed `AUTO_RESPOND` routing through the
  fallback path.

## Open items / deferred

- **Live context-aware LLM classification** was not exercised end-to-end (Ollama
  was down during verification); the wire contract is locked by the integration
  test instead.
- **Auto-respond reply generation** — needs its own LLM call + storage/endpoint.
- **Multi-instance chat-service** would interleave `rightPush` into the same key
  and duplicate entries (single instance today; the per-conversation stripe lock
  is in-JVM, so a distributed lock would be needed across instances, and a
  per-conversation SET of messageIds would dedupe regardless of instance count).
- ~~**Backfill race**~~ **Fixed** — backfills are serialized per conversation with
  a 16-stripe JVM lock; the seed includes only strictly-prior ids and every path
  appends the current message exactly once, so concurrent first-posts no longer
  double-seed or drop a message. Covered by `concurrentFirstPosts_backfillRunsOnceAndBothMessagesAppend`
  in `ConversationMemoryWriterTest`.
- **Unbounded backfill query** — a cache miss loads the full conversation history
  (`findByConversationIdOrderByCreatedAtAscIdAsc`) just to keep the last N. Fine at
  this scale; a `Pageable`/`LIMIT` query is the production-shaped fix if a long-running
  conversation makes that read outweigh the few messages it keeps.
- **Transactional outbox table** still pending for the AFTER_COMMIT Kafka
  publishers (same exposure as noted in Phase 4).
