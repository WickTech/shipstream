CREATE TABLE outbox_events (
    event_id       UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id   VARCHAR(64) NOT NULL,
    event_type     VARCHAR(64) NOT NULL,
    payload        JSONB       NOT NULL,
    published      BOOLEAN     NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE processed_events (
    topic      VARCHAR(128) NOT NULL,
    partition  INT          NOT NULL,
    offset_val BIGINT       NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (topic, partition, offset_val)
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (published, created_at)
    WHERE published = false;
