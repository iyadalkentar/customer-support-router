# Phase 1 Status Note — Foundations

## What was built
- `chat-service` scaffolded on Spring Boot 4.1 (Java 21, Maven), with Web, Data JPA,
  PostgreSQL driver, Validation, Actuator, Flyway.
- Postgres running locally via Docker Compose (credentials in `.env`, not committed).
- Schema managed via Flyway (`V1__create_initial_schema.sql`): `conversations`,
  `messages`, `tickets`, with FK indexes on `conversation_id`.
- JPA entities (`Conversation`, `Message`, `Ticket`) mapped to the schema, with
  `@CreationTimestamp`/`@UpdateTimestamp` for audit columns, enum-backed `status`
  fields (`ConversationStatus`, `TicketStatus`), and bidirectional helper methods
  (`addMessage`/`addTicket`) to keep both sides of the relationship in sync.
- `spring.jpa.hibernate.ddl-auto: validate` — entities are checked against the live
  schema at startup rather than relying on Hibernate to manage DDL. Migration-first
  workflow: write migration → run it → update entity to match.
- `POST /messages` — validated DTO in, `202 Accepted` with a `MessageResponse` DTO out.
  Delegates to `ConversationService.addMessageToConversation`, which owns the single
  transactional boundary (look up or create conversation, persist message).
- `GET /conversations/{id}/messages` — read-back endpoint, ordered by `createdAt`,
  backed by a repository query method (not the entity's lazy collection).
- `GlobalExceptionHandler` (`@RestControllerAdvice`) maps `ConversationNotFoundException`
  → 404, `ConversationClosedException` → 409, validation failures → 400.
- Test coverage: `ConversationServiceTest` (Mockito, covers the branching logic in
  `getOrCreateConversation` and the message-persistence fix below), plus
  `MessageFlowIntegrationTest` (Testcontainers + real Postgres, full HTTP round trip
  including Flyway migrations running against the container).

## Key decisions / deviations from the original plan
- **`conversationId` handling:** `POST /messages` requires an explicit `conversationId`,
  or omits it to create a new conversation. Deliberately did **not** implement "append
  to customer's last open conversation" — that requires a customer/session identity
  concept that doesn't exist yet (auth is an explicit v2 item). Revisit when a customer
  identity model is introduced, likely alongside the frontend in Phase 7.
- **DTOs are manually mapped** (static factory methods, e.g. `MessageResponse.from(...)`),
  not MapStruct/ModelMapper — reassess if `ai-classifier-service` ends up with enough
  DTO variants that manual mapping becomes repetitive boilerplate.
- **Entities never leave the persistence layer** — controllers only see DTOs, avoiding
  Jackson lazy-loading/recursion issues with the `@OneToMany`/`@ManyToOne` graph.
- **`@Data` avoided on JPA entities** in favor of `@Getter`/`@Setter` +
  `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` on `id` only, and `@ToString`
  with associations excluded — avoids lazy-init exceptions and equals/hashCode
  instability from Lombok's all-fields defaults.
- **`MessageResponse` is a record**, not a Lombok class — needed for Jackson 3's
  constructor-based deserialization in the integration test (no-arg constructor +
  setters would have also worked but is more surface area for no benefit).

## Framework version notes (Boot 4.1 / Framework 7 — bleeding edge, sparse docs)
Worth remembering for `ai-classifier-service`, since these aren't yet well-covered in
tutorials/Stack Overflow:
- `TestRestTemplate` is soft-deprecated → use `RestTestClient`
  (`org.springframework.test.web.servlet.client.RestTestClient`), enabled via
  `@AutoConfigureRestTestClient`. API mirrors `RestClient`, not `WebTestClient` —
  use `.body(...)`, not `.bodyValue(...)`.
- Testcontainers 2.0 moved `PostgreSQLContainer` to `org.testcontainers.postgresql`
  (was `org.testcontainers.containers`) — old package is deprecated.
- Hibernate 7 auto-detects dialect from the JDBC URL — don't set
  `hibernate.dialect` explicitly (triggers a deprecation warning).
- `spring.jpa.open-in-view` defaults to `true` and logs a warning — set explicitly
  (we set it to `false`).

## Open questions carried into Phase 2
- **Kafka event payload shape** for `incoming-messages`: message ID only (consumer
  re-fetches from Postgres) vs. full message payload embedded in the event. Needs a
  decision before wiring the producer, since it affects whether the consumer needs
  any additional read access/methods beyond what exists today.
- Whether `ai-classifier-service` should share the `com.ikdev.customersupportrouter`
  group and any common DTOs/contracts with `chat-service`, or stay fully independent
  until a clear need for a shared module emerges.
