-- Create audit_log table (append-only, no temporal versioning needed)
CREATE TABLE moderation.audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(100) NOT NULL,
    target_id UUID NOT NULL,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_audit_log_actor_id ON moderation.audit_log(actor_id);
CREATE INDEX idx_audit_log_target ON moderation.audit_log(target_type, target_id);
CREATE INDEX idx_audit_log_created_at ON moderation.audit_log(created_at);
