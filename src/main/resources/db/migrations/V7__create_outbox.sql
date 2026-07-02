-- Transactional outbox: events are staged here in the task's transaction, then relayed to Kafka.
-- No RLS -- the relay publishes across all tenants (the tenant is carried in the payload).
CREATE TABLE outbox (
    id             uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    aggregate_type varchar(50)  NOT NULL,   -- e.g. "task"
    aggregate_id   varchar(255) NOT NULL,   -- task id; used as the Kafka message key (ordering)
    event_type     varchar(50)  NOT NULL,   -- CREATED / UPDATED / COMPLETED / DELETED
    topic          varchar(255) NOT NULL,
    payload        jsonb        NOT NULL,   -- serialized TaskEvent
    created_at     timestamptz  NOT NULL DEFAULT now(),
    published_at   timestamptz             -- NULL until the relay publishes the row
);

-- The relay scans unpublished rows oldest-first; a partial index keeps that cheap as the table grows.
CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;

GRANT SELECT, INSERT, UPDATE, DELETE ON outbox TO app_rls;
