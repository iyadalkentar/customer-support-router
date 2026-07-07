ALTER TABLE messages
ADD COLUMN intent VARCHAR(50),
ADD COLUMN sentiment VARCHAR(50),
ADD COLUMN urgency VARCHAR(50),
ADD COLUMN trace_id UUID;

CREATE INDEX idx_messages_trace_id ON messages(trace_id);
