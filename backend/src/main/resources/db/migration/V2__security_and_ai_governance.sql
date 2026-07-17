ALTER TABLE question_logs
    ADD COLUMN asked_by VARCHAR(120) NOT NULL DEFAULT 'legacy',
    ADD COLUMN domain VARCHAR(80),
    ADD COLUMN model_name VARCHAR(120),
    ADD COLUMN prompt_version VARCHAR(80),
    ADD COLUMN input_tokens INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN output_tokens INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN fallback BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor VARCHAR(120) NOT NULL,
    method VARCHAR(12) NOT NULL,
    path VARCHAR(500) NOT NULL,
    response_status INTEGER NOT NULL,
    remote_address VARCHAR(80),
    user_agent VARCHAR(300),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_audit_logs_actor_created ON audit_logs(actor, created_at DESC);
CREATE INDEX idx_audit_logs_path_created ON audit_logs(path, created_at DESC);
CREATE INDEX idx_question_logs_domain_created ON question_logs(domain, created_at DESC);
