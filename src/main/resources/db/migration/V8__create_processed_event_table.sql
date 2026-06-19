-- V8__create_processed_event_table.sql
CREATE TABLE PROCESSED_EVENT (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    consumer_name TEXT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(event_id, consumer_name)
);
