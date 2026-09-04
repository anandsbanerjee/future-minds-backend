CREATE TABLE parent_consent (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_account_id BIGINT NOT NULL,
    consent_type VARCHAR(50) NOT NULL,
    consent_version VARCHAR(50) NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_parent_consent_parent_account
        FOREIGN KEY (parent_account_id) REFERENCES parent_account (id)
);
