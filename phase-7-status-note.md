# Phase 7 Status Note — React Frontend (complete)

This note records what shipped in Phase 7, the key decisions/corrections made
during implementation, and items deferred to later phases. Phase 7 landed in
three incremental OpenSpec changes rather than one — see
[`openspec/changes/archive/2026-08-23-add-frontend-chat-ui/`](openspec/changes/archive/2026-08-23-add-frontend-chat-ui/),
[`openspec/changes/archive/2026-08-24-add-conversations-list-ui/`](openspec/changes/archive/2026-08-24-add-conversations-list-ui/),
and [`openspec/changes/archive/2026-08-24-add-tickets-ui/`](openspec/changes/archive/2026-08-24-add-tickets-ui/)
for the full planning trail (proposal, design, specs, tasks per change).

## What was built

- **`frontend/` scaffold** — Vite + React 19 + TypeScript SPA, no SSR/router
  framework (rejected Next.js — no server-rendering or routing need this app
  justifies). CSS Modules for styling, driven off design tokens in `index.css`
  (Minimalism & Swiss Style palette: navy primary, sparing accent/destructive
  colors, Outfit/Work Sans type pair).
- **Chat conversation view** (`add-frontend-chat-ui`):
  - `api/client.ts` — typed fetch wrapper (`VITE_API_BASE_URL`, default
    `http://localhost:8081`), parses `ErrorResponse` into a typed `ApiError`.
  - `useConversation` — SWR hook polling `GET /conversations/{id}/messages`
    every ~3s so classification (`intent`/`sentiment`/`urgency`/
    `routingDecision`), which lands asynchronously via Kafka after the
    initial `POST /messages` response, becomes visible without a manual
    reload.
  - `useSendMessage` — `POST /messages`, surfaces 409 (closed conversation)/
    400 (validation)/network errors via `ErrorBanner` without clearing the
    composer's draft text.
  - `ChatWindow` / `MessageList` / `MessageBubble` / `ClassificationBadges` /
    `MessageComposer` — each a top-level named component (no inline
    component definitions in render bodies, per project React conventions).
  - Active conversation id persisted in `localStorage` via
    `useActiveConversationId` (versioned key) — no global store; a single
    conversation id was the whole cross-cutting state need at this stage.
  - `chat-service` CORS: `CorsConfig` bean + `app.cors.allowed-origins`
    property (default `http://localhost:5173`), explicit allow-list, no
    wildcard origin — no `@CrossOrigin`/CORS config existed before this
    change.
- **Conversations list** (`add-conversations-list-ui`):
  - `useConversations` — SWR hook polling `GET /conversations` (already
    sorted newest-first, server-capped — no new backend endpoint needed).
  - `ConversationList` / `ConversationListItem` — panel + row, mirroring the
    chat view's component split; `App.tsx` reshaped into a two-pane shell
    (list + `ChatWindow`).
  - `useActiveConversationId` stays the single source of truth: selecting a
    row and "New conversation" (`setConversationId(null)`) both go through
    the existing setter — no separate "draft" state introduced.
  - List and message polling stay decoupled (independent SWR keys); a
    just-sent message updates the chat window immediately, and the list
    picks up the conversation's new `updatedAt` on its own next poll tick.
- **Tickets view** (`add-tickets-ui`):
  - `api/client.ts` gains `getTickets(status?)` and
    `getConversationTickets(conversationId)`; `api/types.ts` gains
    `TicketResponse`/`TicketStatus`.
  - `useTickets(status)` / `useConversationTickets(conversationId)` — SWR
    hooks polling `GET /tickets[?status=]` and
    `GET /conversations/{id}/tickets`; status filtering happens server-side
    (not fetched-then-filtered client-side).
  - `TicketList` / `TicketListItem` — top-level "Tickets" view, mirroring
    `ConversationList` / `ConversationListItem`.
  - `TicketPanel` — renders inside the active conversation's `ChatWindow`,
    backed by its own `useConversationTickets` hook (kept independent of
    `useConversation` — separate backend calls, separate concerns).
  - Top-level "Conversations" / "Tickets" navigation via a plain
    `view: 'conversations' | 'tickets'` state in `App.tsx` — no
    `react-router`, since the app is still a single route with two tabs.
    Selecting a ticket row flips `view` back to `'conversations'` and
    activates its conversation through the same `setConversationId` setter
    used by the conversations list.
  - Reused `ui/Badge` and `ui/Button` primitives; no new UI library. Status
    badges carry a text label alongside color (never color-only); list rows
    and tabs are real `<button>`/`role="tab"` elements with visible
    `:focus-visible` outlines, not bare `div onClick`.
- **Deployment**: `frontend/Dockerfile` + `nginx.conf` — optional static
  build served by nginx (`docker compose up -d --build frontend`) as an
  alternative to the Vite dev server, both on `:5173`. See
  [`frontend/README.md`](frontend/README.md).

## Key decisions / corrections made during implementation

- **Polling over WebSockets/SSE, throughout** — every SWR hook (`useConversation`,
  `useConversations`, `useTickets`, `useConversationTickets`) polls on a 3s-class
  `refreshInterval` rather than adding real-time transport. `chat-service` has
  no push infrastructure today, and at demo scale the added request volume is
  cheap; each change's design doc revisits this as the first thing to
  reconsider if the app grows past single-agent/demo use.
- **No router adopted for two tabs** — `view` is local `App.tsx` state, not
  `react-router`. Trade-off accepted: no deep-linking or back-button support
  for the Tickets view; worth revisiting only if the app grows more views.
- **No global state library at any point** — `useActiveConversationId`
  (localStorage-backed) remained the single piece of cross-cutting state
  through all three changes; Redux/Zustand was considered and rejected each
  time as unjustified for this scope.
- **Independent SWR keys over cross-hook `mutate()` coupling** — list/panel
  views are allowed to lag up to one poll interval behind a just-sent
  message or just-created ticket, rather than wiring `useSendMessage` to
  force-refresh sibling hooks. Matches each spec's "next refresh" wording
  and keeps the hooks decoupled.
- **CORS added as a durable capability, not a dev hack** — explicit
  origin allow-list via `app.cors.allowed-origins` (Spring property, no
  wildcard), because `chat-service` had zero CORS configuration before this
  phase and the frontend is a separate origin from it in every deployment
  topology considered.

## Tests

- No automated frontend test suite was added in Phase 7 (no `*.test.*` /
  `*.spec.*` files exist under `frontend/src/`) — verification was manual
  (dev server + live backend) per change, consistent with each design doc's
  scope. This is the main gap carried into the deferred items below.
- `chat-service` CORS config is covered by the backend integration test suite
  (`mvn test`, Testcontainers) added alongside `CorsConfig`.

## Manual verification

- Ran `chat-service` (`:8081`), `ai-classifier-service` (`:8083`), and the
  frontend dev server (`:5173`) together against local Postgres/Kafka/Redis;
  sent messages through the chat view and confirmed classification badges
  populate after the async Kafka round-trip without a manual reload.
- Exercised the two-pane conversations shell: creating a new conversation,
  switching between existing ones via the list, and confirming the active
  row highlight and chat window stay in sync.
- Exercised the Tickets tab: filtering by status, selecting a ticket row to
  jump back into its conversation, and confirming the in-conversation
  `TicketPanel` reflects ticket state for the active conversation.
- Verified the CORS allow-list: requests from `http://localhost:5173` to
  `chat-service` succeed; the browser's own CORS enforcement covers the
  negative case (origins outside the allow-list are rejected client-side).

## Open items / deferred

- **No automated frontend tests** (component/hook unit tests, e2e) — the
  biggest gap; not scoped into any of the three Phase 7 changes.
- **No pagination UI** for conversations or tickets lists, even though both
  backend endpoints cap results server-side (`GET /conversations`,
  `GET /tickets` at 200) — accepted at current single-agent/demo scale in
  both `conversations-list-ui` and `tickets-ui` design docs.
- **No ticket detail page** — `GET /tickets/{id}` exists on the backend but
  is unused by the frontend; reserved for a future drill-in view.
- **No dark mode** — the chosen design system supports it and tokens are
  structured as CSS variables for an additive follow-up, but Phase 7 ships
  light mode only.
- **No deep-linking / shareable URLs** for the Tickets view, a consequence
  of the no-router decision above.
- Deferred items carried over from Phase 4/5/6 (transactional outbox table,
  auto-respond reply generation, unbounded backfill query, multi-instance
  `chat-service` support, alerting rules, log aggregation/tracing) are
  unchanged by this phase — see [`phase-6-status-note.md`](./phase-6-status-note.md).
