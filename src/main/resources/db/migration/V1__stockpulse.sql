CREATE TABLE stores (
    id              VARCHAR(10)  PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    lat             DOUBLE       NOT NULL,
    lng             DOUBLE       NOT NULL,
    online_enabled  BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE inventory (
    store_id      VARCHAR(10) NOT NULL,
    sku           VARCHAR(40) NOT NULL,
    on_hand       INT         NOT NULL,
    reserved      INT         NOT NULL DEFAULT 0,
    safety_stock  INT         NOT NULL DEFAULT 0,
    version       BIGINT      NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP   NOT NULL,
    PRIMARY KEY (store_id, sku),
    CONSTRAINT fk_inventory_store FOREIGN KEY (store_id) REFERENCES stores (id)
);
CREATE INDEX idx_inventory_sku ON inventory (sku);

CREATE TABLE reservations (
    id          VARCHAR(36) PRIMARY KEY,
    store_id    VARCHAR(10) NOT NULL,
    sku         VARCHAR(40) NOT NULL,
    qty         INT         NOT NULL,
    order_ref   VARCHAR(40) NOT NULL,
    status      VARCHAR(10) NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    CONSTRAINT uq_reservation_order UNIQUE (order_ref, sku, store_id)
);
CREATE INDEX idx_reservations_expiry ON reservations (status, expires_at);

CREATE TABLE processed_events (
    event_id      VARCHAR(64) PRIMARY KEY,
    processed_at  TIMESTAMP   NOT NULL
);
CREATE INDEX idx_processed_at ON processed_events (processed_at);
