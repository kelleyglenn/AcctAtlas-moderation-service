-- Create moderation schema
CREATE SCHEMA IF NOT EXISTS moderation;

-- Enable btree_gist for exclusion constraints with temporal tables
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Create moderation_items table
CREATE TABLE moderation.moderation_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type VARCHAR(50) NOT NULL,
    content_id UUID NOT NULL,
    submitter_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    priority INTEGER NOT NULL DEFAULT 0,
    reviewer_id UUID,
    reviewed_at TIMESTAMPTZ,
    rejection_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sys_period TSTZRANGE NOT NULL DEFAULT tstzrange(NOW(), NULL)
);

-- Create history table for temporal data
CREATE TABLE moderation.moderation_items_history (
    LIKE moderation.moderation_items
);

-- Create trigger function for temporal versioning
CREATE OR REPLACE FUNCTION moderation.versioning_trigger()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        INSERT INTO moderation.moderation_items_history
        SELECT OLD.id, OLD.content_type, OLD.content_id, OLD.submitter_id,
               OLD.status, OLD.priority, OLD.reviewer_id, OLD.reviewed_at,
               OLD.rejection_reason, OLD.created_at,
               tstzrange(lower(OLD.sys_period), NOW());
        NEW.sys_period = tstzrange(NOW(), NULL);
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO moderation.moderation_items_history
        SELECT OLD.id, OLD.content_type, OLD.content_id, OLD.submitter_id,
               OLD.status, OLD.priority, OLD.reviewer_id, OLD.reviewed_at,
               OLD.rejection_reason, OLD.created_at,
               tstzrange(lower(OLD.sys_period), NOW());
        RETURN OLD;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Attach trigger
CREATE TRIGGER moderation_items_versioning
    BEFORE UPDATE OR DELETE ON moderation.moderation_items
    FOR EACH ROW EXECUTE FUNCTION moderation.versioning_trigger();

-- Indexes
CREATE INDEX idx_moderation_items_status ON moderation.moderation_items(status);
CREATE INDEX idx_moderation_items_content_id ON moderation.moderation_items(content_id);
CREATE INDEX idx_moderation_items_submitter_id ON moderation.moderation_items(submitter_id);
CREATE INDEX idx_moderation_items_created_at ON moderation.moderation_items(created_at);
