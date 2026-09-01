ALTER TABLE parent_account
    ADD COLUMN marketing_opt_in BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE parent_profile_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_account_id BIGINT NOT NULL,
    change_type VARCHAR(50) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_parent_profile_audit_parent_account
        FOREIGN KEY (parent_account_id) REFERENCES parent_account (id)
);
