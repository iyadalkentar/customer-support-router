-- Phase 4: routing decision on messages + ticket escalation indexes

ALTER TABLE messages
ADD COLUMN routing_decision VARCHAR(50);

-- Supports the "find existing OPEN ticket for a conversation" escalation lookup.
CREATE INDEX idx_tickets_conversation_status ON tickets(conversation_id, status);

-- Enforces the Phase 4 invariant: at most one OPEN ticket per conversation.
-- Drop this index if a conversation ever legitimately needs more than one
-- OPEN ticket (e.g. multiple concurrent escalation streams).
CREATE UNIQUE INDEX uq_tickets_one_open_per_conversation
ON tickets(conversation_id) WHERE status = 'OPEN';
