# Customer Support Router
[![CI](https://github.com/iyadalkentar/customer-support-router/actions/workflows/ci.yml/badge.svg)](https://github.com/iyadalkentar/customer-support-router/actions/workflows/ci.yml)
[![CD](https://github.com/iyadalkentar/customer-support-router/actions/workflows/cd.yml/badge.svg)](https://github.com/iyadalkentar/customer-support-router/actions/workflows/cd.yml)

An AI-powered customer support routing pipeline: incoming chat messages are classified
by an LLM (intent, sentiment, urgency) and routed to auto-response, human escalation,
or ticket creation. Built as a portfolio project demonstrating AI-in-the-loop distributed
systems — messaging, caching, and observability included end-to-end.

## Status
✅ **Phase 7 (React frontend) complete** — full pipeline now has a UI: chat
conversation view, conversations list, and a read-only tickets view, all on
top of the Phase 1-6 backend pipeline.

- [x] Repo scaffolding
- [x] `chat-service` skeleton (Spring Boot 4.1)
- [x] Postgres via Docker Compose
- [x] Schema migrations (Flyway)
- [x] `POST /messages` ingest endpoint
- [x] `GET /conversations/{id}/messages` read-back endpoint (now exposes `intent` / `sentiment` / `urgency`)
- [x] Unit + integration tests (Testcontainers)
- [x] Kafka wiring (KRaft mode, no Zookeeper)
- [x] Transactional outbox-style event publishing (`AFTER_COMMIT`)
- [x] `ai-classifier-service` — LLM-backed `LlmClient` with retry + bounded timeout + UNKNOWN/NEUTRAL fallback on classifier failure
- [x] Closing leg: `chat-service` consumes `classification-results` and updates `Message` columns
- [x] End-to-end integration test for the classification round-trip
- [x] Routing/escalation logic (TDD — `AUTO_RESPOND` / `ESCALATE_TO_HUMAN` / `CREATE_TICKET`, one OPEN ticket per conversation, `escalations` topic)
- [x] Conversation memory (Redis)
- [x] Observability (Prometheus/Grafana)
- [x] React frontend (chat view, conversations list, tickets view)

See [`customer-support-router-plan.md`](./customer-support-router-plan.md) for the full
architecture and phased build plan, [`phase-1-status-note.md`](./phase-1-status-note.md)
for Phase 1 build notes and decisions,
[`phase-2-status-note.md`](./phase-2-status-note.md) for Phase 2 build notes and the
Jackson 2/3 coexistence fix, and
[`phase-3-status-note.md`](./phase-3-status-note.md) for Phase 3 build notes, the
closing leg, and items deferred into Phase 4+, and
[`phase-4-status-note.md`](./phase-4-status-note.md) for Phase 4 routing/escalation
build notes, the rule table, and the one-OPEN-ticket-per-conversation decision, and
[`phase-5-status-note.md`](./phase-5-status-note.md) for Phase 5 Redis conversation
memory build notes, and
[`phase-6-status-note.md`](./phase-6-status-note.md) for Phase 6 observability
build notes and the Gemini provider swap, and
[`phase-7-status-note.md`](./phase-7-status-note.md) for Phase 7 frontend build
notes (chat view, conversations list, tickets view) and deferred items (no
automated frontend tests, no pagination UI, no dark mode).

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

The whole pipeline — Postgres, Kafka, Redis, `chat-service`, `ai-classifier-service`,
the frontend, Prometheus, and Grafana — runs as one Docker Compose stack:

```bash
cp .env.example .env   # fill in local credentials, in particular GEMINI_API_KEY
docker compose up -d --build
```

`chat-service` is on :8081, `ai-classifier-service` on :8083, the frontend on :5173.
`GEMINI_API_KEY` (in `.env`) is required — `llm.provider` defaults to `gemini` in both
services. Switch to a local model with `llm.provider=ollama` if you don't want to use the
Gemini API for local dev (requires an Ollama instance reachable from the container, e.g.
`http://host.docker.internal:11434`).

Rebuild a single service after changing its code with `docker compose up -d --build
chat-service` (or `ai-classifier-service`, `frontend`) rather than rebuilding the whole stack.

### Hybrid dev mode (hot reload)

For faster backend iteration, run just the infra in Docker and the Java services on the
host with `spring-boot-devtools` hot reload:

```bash
docker compose up -d postgres kafka redis prometheus grafana

# Terminal 1 — chat-service on :8081
cd chat-service
mvn spring-boot:run

# Terminal 2 — ai-classifier-service on :8083
cd ai-classifier-service
mvn spring-boot:run

# Terminal 3 — frontend dev server on :5173
cd frontend
npm install
npm run dev
```

Note: `prometheus.yml` scrapes the compose service names (`chat-service:8081`,
`ai-classifier-service:8083`), so in hybrid mode those two panels won't have data unless
you temporarily point the scrape targets at `host.docker.internal:8081`/`:8083` instead.

Prometheus is at http://localhost:9090; Grafana is at http://localhost:3000 (`admin`/`admin`)
with the "Customer Support Router" dashboard auto-provisioned (throughput, classification
latency, escalations, Kafka consumer lag, error rate). Both services must be running and have
handled at least one message for their panels to show data.

An optional `kafka-exporter` service adds broker-side consumer-group lag as a second series
on the "Kafka Consumer Lag" panel; it's opt-in since it's redundant with the JVM-side lag
metric the dashboard already gets from each service:
```bash
docker compose --profile monitoring up -d kafka-exporter
```

### Frontend cross-origin configuration

`chat-service` enforces CORS (Cross-Origin Resource Sharing) to allow the React frontend to safely fetch from its REST endpoints. The allowed origins are configured via the `app.cors.allowed-origins` property in `chat-service/src/main/resources/application.yml`, which defaults to `http://localhost:5173` (the local frontend dev server). Origins not in this list will be blocked by the browser's CORS policy.

To allow additional origins in production:
```yaml
app:
  cors:
    allowed-origins: "http://localhost:5173,https://yourdomain.com"
```

Running tests requires Docker (Testcontainers spins up real Postgres/Kafka/Redis containers):
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
├── chat-service/          Spring Boot: ingest, REST, Postgres, Kafka producer/consumer, classification read-back
├── ai-classifier-service/ Spring Boot: LLM-backed classification, Kafka consumer/publisher
├── frontend/              React chat UI: conversation view, conversations list, tickets view
├── docker-compose.yml     Full stack: chat-service, ai-classifier-service, frontend, Postgres, Kafka (KRaft), Redis, Prometheus, Grafana (+ optional kafka-exporter)
├── prometheus.yml         Scrape config for chat-service, ai-classifier-service, kafka-exporter
├── grafana/provisioning/  Datasource + dashboard provisioning ("Customer Support Router" dashboard)
├── customer-support-router-plan.md
├── phase-1-status-note.md
├── phase-2-status-note.md
├── ...
├── phase-6-status-note.md
└── phase-7-status-note.md
```
