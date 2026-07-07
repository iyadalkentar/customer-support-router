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

## Jackson 2 vs. Jackson 3 — required coexistence (not incidental)
This was the most time-consuming issue in the phase and is worth documenting
precisely, since it's non-obvious and specific to being on Boot 4.1:

- **Spring Boot 4.1 uses Jackson 3** (`tools.jackson.*` groupId) for its own
  auto-configured `ObjectMapper` and Jackson auto-configuration.
- **Spring Kafka's `JsonSerializer`/`JsonDeserializer` (spring-kafka 4.1.0)
  still depend on Jackson 2** (`com.fasterxml.jackson.*`) classes at runtime —
  specifically `com.fasterxml.jackson.core.type.TypeReference`.
- Initial attempt used `spring.json.objectmapper: "customObjectMapper"` in
  `application.yml`, pointing the Kafka serializers at a named Spring bean that
  was never actually defined. This caused a context startup failure
  (missing-bean style error) — traced back after the fact; the earlier "fails
  to boot, fixed by a 0.0.0.0-related config" note from earlier in the phase
  was likely a misremembered version of this same class of issue, not a
  distinct network-binding problem. No confirmed root cause for a genuine
  `0.0.0.0` listener issue was found — flagged as unresolved rather than
  asserting a specific fix.
- **Fix, step 1:** removed `spring.json.objectmapper` entirely. Spring Kafka's
  `JsonSerializer`/`JsonDeserializer` build their own internal Jackson 2
  `ObjectMapper` by default when no custom mapper is configured, so this was
  unnecessary.
- **Attempted follow-up cleanup:** removed the explicit Jackson 2 dependencies
  (`jackson-core`, `jackson-databind`, `jackson-datatype-jsr310`, all
  `2.17.1`) from `chat-service/pom.xml`, on the assumption Boot 4.1's Jackson 3
  migration made them redundant.
- **This broke the consumer at startup**:
  `NoClassDefFoundError: com/fasterxml/jackson/core/type/TypeReference`,
  surfaced via `KafkaException: Failed to construct kafka consumer` →
  `Failed to start bean 'internalKafkaListenerEndpointRegistry'`. Confirmed
  that Spring Kafka's JSON (de)serializers need Jackson 2 present on the
  classpath regardless of what Boot itself uses internally.
- **Final fix:** restored all three Jackson 2 dependencies as explicit
  `pom.xml` entries (`2.17.1`). They are required for Spring Kafka's
  serializers, not leftover/redundant cruft from a Boot 3.x-era tutorial as
  initially suspected. `jackson-datatype-jsr310` in particular is needed for
  `OffsetDateTime` (used in `MessageEvent.createdAt`) to serialize/deserialize
  correctly through Spring Kafka's internal Jackson 2 mapper.
- **Net result:** Jackson 2 and Jackson 3 legitimately coexist on the
  classpath in this project — Boot's own machinery uses Jackson 3, Spring
  Kafka's JSON serialization uses Jackson 2. This isn't a version conflict to
  "resolve," just a fact of the current library versions being on different
  Jackson majors at different paces.

## Open questions carried into Phase 3
- Whether Spring Kafka will release a Jackson-3-native serializer in a future
  version — worth a quick check before Phase 3 in case it changes the
  dependency setup again.
- `MessageEventConsumer` is currently a same-service stub (consumer lives in
  `chat-service`, per the Phase 2 plan's "can be same service initially").
  Phase 3 needs a decision: keep the consumer in `chat-service` and have it
  call out to `ai-classifier-service` (REST or another Kafka hop), or move the
  `@KafkaListener` into `ai-classifier-service` directly and have it consume
  `incoming-messages` on its own group ID.
- `KAFKA_ADVERTISED_LISTENERS` uses `127.0.0.1` — fine for host-run services
  today, needs revisiting if `ai-classifier-service` or `chat-service` are
  ever containerized.
