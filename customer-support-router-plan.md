# AI-Powered Customer Support Router — Project Plan

## Purpose
Portfolio project demonstrating real-world experience with AI-in-the-loop distributed systems (inspired by prior MAIDS work). Built to be inspectable by hiring managers and to showcase: AI integration, distributed systems, messaging, caching, security basics, and observability — without over-scoping.

## Scope decision
Keep this build **narrow and complete** rather than wide and half-finished.

**In scope (core pipeline):**
- Customer chat (simple)
- AI intent/sentiment classification
- Routing logic based on classification
- Escalation to human
- Conversation memory (short-term, via Redis)
- Basic ticket creation
- Observability (Prometheus + Grafana)
- Minimal React front end

**Explicitly deferred (v2 / stretch goals):**
- RAG over uploaded documents (needs vector DB + embedding pipeline — treat as its own project later)
- Full JWT authentication
- Rate limiting
- API Gateway layer
- Kubernetes deployment
- CI/CD (GitHub Actions)
- Dedicated analytics dashboard beyond Grafana

These can be bolted on later without re-architecting, since the core is layered cleanly from the start.

## Architecture

```
React (simple chat UI)
   ↓
Spring Boot (chat-service) — REST ingest, Postgres persistence
   ↓
Kafka (incoming-messages topic)
   ↓
AI Classifier Service (Spring Boot) — calls LLM via provider-agnostic interface
   ↓
Routing logic — AUTO_RESPOND / ESCALATE_TO_HUMAN / CREATE_TICKET
   ↓
Redis (conversation memory / short-term context)
   ↓
PostgreSQL (source of truth: conversations, messages, tickets, classifications)
   ↓
Prometheus + Grafana (observability)
```

## Tech stack
- Spring Boot (chat-service + ai-classifier-service, can start as two modules/services)
- Kafka (async decoupling between ingestion and processing)
- Redis (conversation memory, later usable for rate limiting)
- PostgreSQL (persistence)
- Docker Compose (local orchestration: Postgres, Kafka, Zookeeper, Redis, Prometheus, Grafana)
- LLM: **Ollama locally during dev**, swappable to OpenAI/Anthropic later
- React (minimal chat UI, calls Spring Boot REST API directly — no gateway yet)
- Prometheus + Micrometer + Grafana

## LLM provider strategy
Built provider-agnostic from day one so swapping is a config change, not a rewrite.

```java
public interface LlmClient {
    ClassificationResult classify(String prompt);
}

@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
public class OllamaLlmClient implements LlmClient { ... }

@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAiLlmClient implements LlmClient { ... }
```

```yaml
llm:
  provider: ollama   # switch to "openai" or "anthropic" later
  ollama:
    base-url: http://localhost:11434
    model: llama3.1:8b
  openai:
    api-key: ${OPENAI_API_KEY}
    model: gpt-4o-mini
```

**Model choice for Ollama:** `llama3.1:8b` (good balance of quality/speed). Fallback to `phi3:mini` or `qwen2.5:3b` if hardware-constrained.

**Reliability note:** smaller local models are less consistent at strict JSON output than OpenAI. Mitigate with:
- Explicit prompt template demanding JSON-only output, with an example
- One retry/repair attempt on parse failure
- Graceful fallback to a default classification (`UNKNOWN` intent, neutral sentiment) rather than crashing the pipeline — this fallback logic should be kept even after swapping providers, since production systems need to handle malformed LLM output regardless of source.

**Swap timing:** swap to OpenAI/Anthropic once the pipeline is stable (around Phase 6/7), so all early debugging happens on the free local model and API costs are only incurred once things work.

## Repo structure
```
customer-support-router/
├── chat-service/          (Spring Boot: ingest, REST, Postgres)
├── ai-classifier-service/ (Spring Boot: OpenAI/Ollama calls, classification)
├── frontend/              (React chat UI)
├── docker-compose.yml     (Postgres, Kafka, Redis, Grafana, Prometheus)
├── grafana/                (dashboard JSON)
└── README.md               (architecture diagram + how to run)
```

## Phased build plan

### Phase 1 — Foundations (2-3 days)
- Spring Boot skeleton: `chat-service`
- Postgres schema: `conversations`, `messages`, `tickets`
- `POST /api/messages` → persist message, return 202 Accepted
- Docker Compose: Postgres + Kafka + Zookeeper + Redis
- Goal: message in via API, persisted. No AI yet.

### Phase 2 — Kafka wiring (2 days)
- Producer: publish event to `incoming-messages` on ingest
- Consumer: `@KafkaListener` picks up event (can be same service initially)
- Goal: prove async decoupling between ingestion and processing.

### Phase 3 — AI classification service (3-4 days)
- New service: `ai-classifier-service`
- Implement `LlmClient` interface + `OllamaLlmClient`
- Call Ollama with prompt to classify: intent, sentiment, urgency
- Consumer from Phase 2 calls this service, gets structured JSON back
- Store classification result in Postgres alongside the message
- Goal: LLM-in-the-loop classification — the centerpiece of the project.

### Phase 4 — Routing + escalation logic (2 days)
- Route based on classification: `AUTO_RESPOND`, `ESCALATE_TO_HUMAN`, `CREATE_TICKET`
- Escalation creates a `ticket` row, optionally publishes to `escalations` topic
- Simple rule example: sentiment=negative + urgency=high → escalate; else auto-respond
- Goal: business logic driven by AI output, not just "call LLM and print result."

### Phase 5 — Conversation memory (2 days)
- Redis: store last N messages per conversation (list/hash keyed by conversation ID)
- Pull recent context from Redis when calling AI service, include in prompt
- Postgres remains source of truth; Redis is the fast-access layer
- Goal: context-aware responses, demonstrates caching skill.

### Phase 6 — Observability (2 days)
- Micrometer + Prometheus endpoint (`/actuator/prometheus`)
- Custom metrics: messages processed/sec, classification latency, escalation rate, Kafka consumer lag
- Grafana dashboard with 4-5 panels
- Goal: shows production-operations thinking, not just feature code. Often the most differentiating part for senior roles.
- **Swap LLM provider to OpenAI/Anthropic around this phase.**

### Phase 7 — Minimal React front end (2-3 days)
- Single-page chat UI: input, message list, classification badge per message (e.g., "escalated")
- Calls Spring Boot REST API directly
- Goal: demoable in an interview in under 2 minutes.

**Total estimate:** ~15-18 working days part-time, or 2-2.5 weeks full-time. Prompt tuning and Kafka consumer debugging tend to take longer than expected — budget accordingly.

## Working approach
- Code is being written by hand (not via Claude Code) — this project/chat is for design discussion, debugging help, and idea exchange, not code generation-and-paste.
- Use one Claude conversation per phase within a shared Project, so each phase discussion is focused but still has this plan as shared context.
- After finishing each phase, add a short status note back into project knowledge (what was built, key decisions made, any deviations from this plan) so later phases don't require re-explaining context.
