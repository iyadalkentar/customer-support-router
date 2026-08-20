# Phase 6 Status Note — Observability + Gemini Provider (complete)

This note records what shipped in Phase 6, the key decisions/corrections made
during implementation, the tests, and items deferred to later phases. See
[`openspec/changes/phase-6-observability/`](openspec/changes/phase-6-observability/)
(proposal, specs, design, tasks) for the full planning trail.

## What was built

- **Micrometer + Prometheus** on both services — `micrometer-registry-prometheus`
  added to both `pom.xml`s; `/actuator/prometheus` exposed via
  `management.endpoints.web.exposure.include=prometheus` +
  `management.prometheus.metrics.export.enabled=true`.
- **chat-service metrics**:
  - `MessageMetrics` — counter `messages.processed`, tagged `service` + `outcome`
    (`success` / `rejected` / `error`, kept distinct so client-caused rejections
    don't pollute an error-rate panel). Wired into `ConversationService` (success)
    and `GlobalExceptionHandler` (rejected/error).
  - `RoutingMetrics` — counter `escalation.event` (not `escalation.created` —
    Prometheus's OpenMetrics client silently strips a `.created` suffix), tagged
    `routing_decision` only (no `conversationId` — unbounded cardinality belongs
    in logs, not metric labels). Wired into `RoutingService`.
- **ai-classifier-service metrics**:
  - `ClassificationMetrics` — timer `classification.latency`, tagged `provider` +
    `result` (`success` / `fallback` / `timeout`). Wraps `ChatModelService.classify`
    in a `finally` block so latency and outcome are recorded on every path.
  - `KafkaConsumerLagMetrics` — binds Micrometer's `KafkaClientMetrics` to each
    consumer created by `DefaultKafkaConsumerFactory` and closes it on
    `consumerRemoved` (avoids leaking the binder's background poller thread on
    consumer restarts). Resolved meter:
    `kafka.consumer.fetch.manager.records.lag.max` (gauge), tagged
    `client-id`/`topic`/`partition` — not a literal `kafka.consumer.lag`, which
    doesn't exist as a Micrometer meter name.
- **Gemini as a selectable LLM provider** — `spring-ai-starter-model-google-genai`
  added to `ai-classifier-service`; `LlmProviderEnvironmentPostProcessor` gained
  a `"gemini"` case mapping to Spring AI's `google-genai` selector value (not the
  literal string `"gemini"`), plus a fail-fast `IllegalStateException` at startup
  if `llm.provider=gemini` and `GEMINI_API_KEY` isn't set. Default `llm.provider`
  changed from `ollama` to `gemini` in both services' `application.yml`/`.yaml`;
  Ollama remains available via `llm.provider=ollama`. Default model:
  `gemini-2.5-flash-lite`.
- **Docker Compose / infra**:
  - `prometheus` (`prom/prometheus:v3.13.0`, :9090, 15d retention) and `grafana`
    (`grafana/grafana:13.2.0`, :3000, `admin`/`admin`) services added.
  - Optional `kafka-exporter` (`danielqsj/kafka-exporter:v1.9.0`, :9308) behind a
    `monitoring` compose profile — broker-side consumer-group lag as a second
    series alongside the always-on JVM-side lag metric.
  - `prometheus.yml` scrapes both services via `host.docker.internal:8081`/`:8083`
    (they run on the host, not as compose services) plus `kafka-exporter:9308`.
  - `grafana/provisioning/dashboards/customer-support-router.json` — 5 panels
    (messages/sec by outcome, classification latency p50/p95/p99, escalations by
    routing decision, Kafka consumer lag, error rate %) with `$service`,
    `$provider`, `$topic`, `$interval` template variables.

## Key decisions / corrections made during implementation

- **`KafkaClientMetrics` is not auto-registered from consumer config alone** —
  it must be explicitly bound to each `Consumer` instance; the design doc's
  original assumption of a metric literally named `kafka.consumer.lag` was
  wrong. Resolved name/tags documented above and locked into the Grafana query.
- **Spring AI's Gemini integration is namespaced `google-genai`**, not `gemini`
  — no `spring-ai-starter-model-gemini` artifact exists, and the selector value
  for `spring.ai.model.chat` is `google-genai`, confirmed by decompiling the
  autoconfigure jar. App-facing config (`llm.provider=gemini`) is unaffected;
  only the underlying Spring AI artifact/property targets changed.
- **`gemini-1.5-flash` (the original model pick) was fully retired** by Google
  on 2025-09-29 — corrected to `gemini-2.5-flash-lite`.
- **No `llm.<provider>.*` indirection layer** — per-provider model/connection
  settings live flatly under each provider's own Spring AI namespace in
  `application.yaml` (`spring.ai.google-genai.*`, `spring.ai.ollama.*`,
  `spring.ai.openai.*`); the postprocessor only maps the provider selector.
- **Prometheus targets `host.docker.internal`, not compose service names** —
  `chat-service`/`ai-classifier-service` run on the host via `mvn spring-boot:run`,
  not as containers, so there's no compose DNS name for Prometheus to resolve.
- **Live Gemini classification was not covered by the automated suite** —
  `GeminiProviderConfigTest` mocks `ChatModel` to verify wiring/parsing without
  calling the paid API, consistent with the project's Testcontainers-only (no
  external network) CI policy. The live-API path is exercised manually instead
  (see Manual verification below).

## Tests

- **chat-service** (suite: **78 green**, including 3 new metrics/observability
  tests over the Phase 5 baseline of 65 — `MessageMetricsTest` (4),
  `RoutingMetricsTest` (6), `PrometheusEndpointIntegrationTest` (2) — plus a fix
  to `ChatServiceApplicationTests.contextLoads` and
  `MessageFlowIntegrationTest` that were broken ahead of this phase).
- **ai-classifier-service** (suite: **29 green**, up from the Phase 5 baseline
  of 13 — new: `ClassificationMetricsTest` (4), `GeminiProviderConfigTest` (2),
  `PrometheusEndpointIntegrationTest` (3); `LlmProviderEnvironmentPostProcessorTest`
  extended to 7 to cover the `gemini` case and config mapping).
- Both suites run via Testcontainers (real Postgres/Kafka/Redis); no test calls
  the live Gemini API — see the correction above.

## Manual verification

- **Live pipeline + Grafana**: sent messages through the running pipeline
  (`chat-service` → Kafka → `ai-classifier-service` → routing) with
  `docker compose up -d postgres kafka redis prometheus grafana` running;
  confirmed the "Customer Support Router" Grafana dashboard panels
  (throughput, classification latency, escalations, Kafka consumer lag, error
  rate) update in real time as messages are processed.
- **Gemini smoke test**: ran locally with a real `GEMINI_API_KEY` and
  `llm.provider=gemini` (the default); sent a message through the pipeline and
  confirmed a non-fallback classification came back from the Gemini API,
  replacing the live-API assertion intentionally kept out of the automated
  suite (see `GeminiProviderConfigTest` above).

## Open items / deferred

- **Alerting rules (PrometheusRule)** — explicitly out of scope for this phase,
  can be layered on top of the existing scrape config later.
- **Log aggregation (Loki) / distributed tracing (Tempo/Zipkin)** — out of
  scope, noted as non-goals in `design.md`.
- Deferred items carried over from Phase 4/5 (transactional outbox table,
  auto-respond reply generation, unbounded backfill query, multi-instance
  `chat-service` support) are unchanged by this phase — see
  [`phase-5-status-note.md`](./phase-5-status-note.md).
