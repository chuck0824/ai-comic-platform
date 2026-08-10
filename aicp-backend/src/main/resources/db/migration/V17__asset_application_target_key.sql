-- V17: persist stable textual consumer identities for project-scoped asset references.
ALTER TABLE asset_applications
    ADD COLUMN target_key VARCHAR(128) NULL;

CREATE INDEX idx_aa_target_key
    ON asset_applications(target_type, target_key);
