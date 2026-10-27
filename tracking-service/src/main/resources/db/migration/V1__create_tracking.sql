CREATE TABLE tracking_events (
    event_id     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_id  UUID        NOT NULL,
    carrier_code VARCHAR(32) NOT NULL,
    location     VARCHAR(256),
    description  TEXT        NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL,
    ingested_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tracking_shipment_id ON tracking_events (shipment_id, occurred_at DESC);

CREATE TABLE processed_events (
    topic        VARCHAR(128) NOT NULL,
    partition    INT          NOT NULL,
    offset_val   BIGINT       NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (topic, partition, offset_val)
);
