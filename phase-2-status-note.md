# Phase 2 Status Note — Kafka Wiring

## What was built
- `docker-compose.yml` updated to run Kafka in **KRaft combined mode**
  (`apache/kafka:3.9.0`, `broker,controller` roles) — Zookeeper dropped entirely.
  Deliberate simplification vs. the original plan, which assumed Zookeeper.
- `MessageEvent` (record) — full-payload representation of a persisted message
  (`messageId`, `conversationId`, `sender`, `content`, `createdAt`, `eventVersion`).
  Resolves the open question from Phase 1: **full payload embedded**, not
  message-ID-only, so the consumer doesn't need a Postgres round-trip to act on
  an event. Includes `eventVersion` for future schema evolution.
- `MessagePersistedEvent` — internal Spring `ApplicationEvent` wrapping a
  `MessageEvent`.
- `ConversationService.addMessageToConversation` publishes a
  `MessagePersistedEvent` via `ApplicationEventPublisher` after persisting the
  message, inside the existing `@Transactional` boundary.
- `MessageEventPublisher` — listens via
  `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` and only
  then sends the `MessageEvent` to the `incoming-messages` Kafka topic, keyed by
  `conversationId` (guarantees per-conversation ordering via partitioning).
  This avoids publishing a Kafka event for a message whose DB transaction later
  rolls back.
- `MessageEventConsumer` — stub `@KafkaListener` on `incoming-messages`, logs
  the received event. Actual handoff to the AI classifier is deferred to
  Phase 3 (`// TODO` in place).
- `application.yml` — added `spring.kafka.*` config: JSON
  serializer/deserializer for producer and consumer, `group-id:
  chat-service-group`, `auto-offset-reset: earliest`, and
  `spring.json.trusted.packages` scoped specifically to the `event` package
  (not `*`).

## Key decisions / deviations from the original plan
- **Zookeeper dropped**, using Kafka's built-in KRaft mode instead
  (`apache/kafka:3.9.0`, single node running both broker and controller roles).
  Simpler local setup, fewer containers, and aligned with where Kafka is
  heading generally. No functional loss for this project's purposes.
- **Full-payload event, not ID-only.** Chosen so the classifier service (Phase
  3) can act on the event directly without a read-back to Postgres. Trade-off:
  any future payload shape change needs to be handled via `eventVersion`
  rather than just changing the DB schema — consumers must tolerate multiple
  versions during rollout.
- **Advertised listener address is `127.0.0.1`** (`KAFKA_ADVERTISED_LISTENERS`),
  which only works because both Spring services currently run on the host, not
  in Docker. Flagged for revisit if either service is containerized later —
  would need to advertise the Docker Compose service name instead.

## Jackson 2 vs. Jackson 3 — resolved by migrating to Jackson-3-native Kafka (de)serializers
This was the most time-consuming issue in the phase. The eventual root cause
was simpler than it looked at first — worth documenting the path taken so the
detour isn't repeated:

- **Spring Boot 4.1 uses Jackson 3** (`tools.jackson.*` groupId) by default.
- Initial Kafka config used `org.springframework.kafka.support.serializer.
  JsonSerializer`/`JsonDeserializer` — these are Spring Kafka's **older,
  Jackson-2-only** classes, now deprecated for removal (deprecated since
  Spring Kafka 4.0) in favor of Jackson-3-native replacements.
- This produced a chain of confusing symptoms while still on the old classes:
  - `spring.json.objectmapper: "customObjectMapper"` pointed at a Spring bean
    that was never defined → context startup failure. (The earlier
    "0.0.0.0-related" boot failure noted earlier in the phase was likely a
    misremembered version of this same issue — no distinct network-binding
    root cause was ever confirmed, so that's logged as unresolved/unconfirmed
    rather than fact.)
  - Removing the explicit Jackson 2 dependencies (assuming Boot 4.1's Jackson
    3 migration made them redundant) broke the consumer at startup:
    `NoClassDefFoundError: com/fasterxml/jackson/core/type/TypeReference`,
    because the old `JsonSerializer`/`JsonDeserializer` genuinely require
    Jackson 2 classes at runtime regardless of what Boot itself uses.
  - Re-adding Jackson 2 dependencies fixed the boot failure, but left the
    project running Jackson 2 and Jackson 3 side by side, with the producer
    (Jackson 2) and a test consumer written against `JacksonJsonDeserializer`
    (Jackson 3) mismatched — a fragile, temporary-feeling state.
- **Actual fix:** switched `application.yml` to Spring Kafka's Jackson-3-native
  classes:
  ```yaml
  producer:
    value-serializer: org.springframework.kafka.support.serializer.JacksonJsonSerializer
  consumer:
    value-deserializer: org.springframework.kafka.support.serializer.JacksonJsonDeserializer
    properties:
      spring.json.value.default.type: com.ikdev.customersupportrouter.chatservice.event.MessageEvent
  ```
- With that change, **all explicit Jackson 2 dependencies were removed from
  `chat-service/pom.xml`** (`jackson-core`, `jackson-databind`,
  `jackson-datatype-jsr310`, all `2.17.1`) and everything — boot, unit tests,
  and the Kafka integration test including `OffsetDateTime` round-tripping —
  works on Jackson 3 alone.
- **Net result:** no Jackson 2/3 coexistence needed. The project is fully on
  Jackson 3, consistent with Boot 4.1's own defaults. The earlier "coexistence
  is required" conclusion was incorrect — it was an artifact of using
  deprecated Spring Kafka classes, not a genuine cross-library requirement.

## Open questions carried into Phase 3
- **Approach note:** routing/escalation logic (`sentiment=negative + urgency=high
  → escalate`, etc.) is pure business logic with no infra dependencies —
  planned to be built test-first (TDD) in Phase 3, unlike Phase 2's
  test-after approach, which fit better while the Kafka event design was
  still being worked out. Rest of Phase 3 (LLM client, Kafka consumer wiring)
  will likely stay test-after for the same reasons as Phase 2.
- `MessageEventConsumer` is currently a same-service stub (consumer lives in
  `chat-service`, per the Phase 2 plan's "can be same service initially").
  Phase 3 needs a decision: keep the consumer in `chat-service` and have it
  call out to `ai-classifier-service` (REST or another Kafka hop), or move the
  `@KafkaListener` into `ai-classifier-service` directly and have it consume
  `incoming-messages` on its own group ID.
- `KAFKA_ADVERTISED_LISTENERS` uses `127.0.0.1` — fine for host-run services
  today, needs revisiting if `ai-classifier-service` or `chat-service` are
  ever containerized.
