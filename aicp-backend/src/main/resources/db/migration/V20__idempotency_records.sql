-- V20: R2-A idempotency records for stage transitions / fork / staleness / AI adopt
CREATE TABLE IF NOT EXISTS idempotency_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    scope VARCHAR(64) NULL,
    request_hash CHAR(64) NOT NULL,
    response_payload MEDIUMTEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_idem_user_key (user_id, idempotency_key),
    KEY idx_idem_expires (expires_at)
);
