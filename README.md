# Customer Support Router

An AI-powered customer support routing pipeline: incoming chat messages are classified
by an LLM (intent, sentiment, urgency) and routed to auto-response, human escalation,
or ticket creation. Built as a portfolio project demonstrating AI-in-the-loop distributed
systems — messaging, caching, and observability included end-to-end.

## Status
🚧 Work in progress — currently in **Phase 1 (Foundations)**.

- [x] Repo scaffolding
- [x] `chat-service` skeleton (Spring Boot 4.1)
- [x] Postgres via Docker Compose
- [x] Schema migrations (Flyway)
- [ ] `POST /api/messages` ingest endpoint
- [ ] Kafka wiring
- [ ] AI classification service
- [ ] Routing/escalation logic
- [ ] Conversation memory (Redis)
- [ ] Observability (Prometheus/Grafana)
- [ ] React frontend

See [`customer-support-router-plan.md`](./customer-support-router-plan.md) for the full
architecture and phased build plan.

## Architecture (target)
React → Spring Boot (chat-service) → Kafka → AI Classifier Service → Routing →
Redis (memory) / Postgres (source of truth) → Prometheus + Grafana

## Tech stack
Spring Boot · Kafka · Redis · PostgreSQL · Docker Compose · Ollama (dev) / OpenAI /
Anthropic (swappable) · React · Prometheus + Grafana

## Running locally
```bash
cp .env.example .env   # fill in local credentials
docker compose up -d postgres
cd chat-service
./mvnw spring-boot:run
```

## Repo structure
customer-support-router/
├── chat-service/          Spring Boot: ingest, REST, Postgres
├── ai-classifier-service/ Spring Boot: LLM calls, classification (not yet started)
├── frontend/              React chat UI (not yet started)
├── docker-compose.yml
└── customer-support-router-plan.md

