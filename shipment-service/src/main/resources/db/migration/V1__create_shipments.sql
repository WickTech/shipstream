CREATE TABLE shipments (
    shipment_id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id           UUID        NOT NULL UNIQUE,
    carrier            VARCHAR(64) NOT NULL,
    tracking_number    VARCHAR(128) NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    estimated_delivery DATE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_shipments_order_id ON shipments (order_id);
CREATE INDEX idx_shipments_status   ON shipments (status);
