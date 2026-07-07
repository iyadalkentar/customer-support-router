# Customer Support Router

An AI-powered customer support routing pipeline: incoming chat messages are classified
by an LLM (intent, sentiment, urgency) and routed to auto-response, human escalation,
or ticket creation. Built as a portfolio project demonstrating AI-in-the-loop distributed
systems — messaging, caching, and observability included end-to-end.

## Status
✅ **Phase 2 (Kafka wiring) complete** — starting **Phase 3 (AI classification service)** next.

- [x] Repo scaffolding
- [x] `chat-service` skeleton (Spring Boot 4.1)
- [x] Postgres via Docker Compose
- [x] Schema migrations (Flyway)
- [x] `POST /messages` ingest endpoint
- [x] `GET /conversations/{id}/messages` read-back endpoint
- [x] Unit + integration tests (Testcontainers)
- [x] Kafka wiring (KRaft mode, no Zookeeper)
- [x] Transactional outbox-style event publishing (`AFTER_COMMIT`)
- [x] Stub consumer on `incoming-messages`
- [ ] AI classification service
- [ ] Routing/escalation logic
- [ ] Conversation memory (Redis)
- [ ] Observability (Prometheus/Grafana)
- [ ] React frontend

See [`customer-support-router-plan.md`](./customer-support-router-plan.md) for the full
architecture and phased build plan, [`phase-1-status-note.md`](./phase-1-status-note.md)
for Phase 1 build notes and decisions, and
[`phase-2-status-note.md`](./phase-2-status-note.md) for Phase 2 build notes, the
Jackson 2/3 coexistence issue, and open questions carried into Phase 3.

## Architecture (target)
```
React → Spring Boot (chat-service) → Kafka → AI Classifier Service → Routing →
Redis (memory) / Postgres (source of truth) → Prometheus + Grafana
```

Kafka runs in KRaft combined mode (`apache/kafka:3.9.0`, broker + controller roles) —
no Zookeeper. Messages are persisted to Postgres first; a Kafka event carrying the full
message payload is published only after the DB transaction commits
(`@TransactionalEventListener(phase = AFTER_COMMIT)`), keyed by `conversationId` for
per-conversation ordering.

## Tech stack
Spring Boot · Kafka (KRaft) · Redis · PostgreSQL · Docker Compose · Ollama (dev) / OpenAI /
Anthropic (swappable) · React · Prometheus + Grafana

## Running locally
```bash
cp .env.example .env   # fill in local credentials
docker compose up -d postgres kafka
cd chat-service
./mvnw spring-boot:run
```

Running tests requires Docker (Testcontainers spins up a real Postgres container):
```bash
./mvnw test
```

> **Note:** `chat-service` runs on Jackson 3 only. Kafka (de)serialization
> uses Spring Kafka's `JacksonJsonSerializer`/`JacksonJsonDeserializer`
> (Jackson-3-native) rather than the deprecated `JsonSerializer`/
> `JsonDeserializer` classes — the latter require Jackson 2 and pulled in a
> confusing dependency detour during Phase 2. See `phase-2-status-note.md`
> for the full story before reintroducing any `com.fasterxml.jackson.*`
> dependency.

## Repo structure
```
customer-support-router/
├── chat-service/          Spring Boot: ingest, REST, Postgres, Kafka producer/consumer
├── ai-classifier-service/ Spring Boot: LLM calls, classification (not yet started)
├── frontend/              React chat UI (not yet started)
├── docker-compose.yml     Postgres + Kafka (KRaft)
├── customer-support-router-plan.md
├── phase-1-status-note.md
└── phase-2-status-note.md
```
