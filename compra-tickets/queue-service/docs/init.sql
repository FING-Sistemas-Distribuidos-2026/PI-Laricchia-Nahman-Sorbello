-- Tablas propias del queue-service
-- tickets, purchases, usuarios viven en la BD de compra-service (aidis)

CREATE TABLE IF NOT EXISTS queue_entries (
                                             id         BIGSERIAL PRIMARY KEY,
                                             user_id    VARCHAR(36) NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id)
    );

CREATE TABLE IF NOT EXISTS event_log (
                                         id         BIGSERIAL PRIMARY KEY,
                                         user_id    VARCHAR(36),
    event_type VARCHAR(50),
    payload    TEXT,
    created_at TIMESTAMP DEFAULT NOW()
    );

CREATE INDEX idx_queue_entries_user_id ON queue_entries(user_id);
CREATE INDEX idx_queue_entries_status  ON queue_entries(status);
CREATE INDEX idx_event_log_user_id     ON event_log(user_id);