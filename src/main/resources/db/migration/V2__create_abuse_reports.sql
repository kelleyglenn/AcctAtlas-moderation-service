-- Create abuse_reports table
CREATE TABLE moderation.abuse_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type VARCHAR(50) NOT NULL,
    content_id UUID NOT NULL,
    reporter_id UUID NOT NULL,
    reason VARCHAR(50) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    resolved_by UUID,
    resolution VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sys_period TSTZRANGE NOT NULL DEFAULT tstzrange(NOW(), NULL)
);

-- Create history table for temporal data
CREATE TABLE moderation.abuse_reports_history (
    LIKE moderation.abuse_reports
);

-- Create trigger function for abuse_reports versioning
CREATE OR REPLACE FUNCTION moderation.abuse_reports_versioning_trigger()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        INSERT INTO moderation.abuse_reports_history
        SELECT OLD.id, OLD.content_type, OLD.content_id, OLD.reporter_id,
               OLD.reason, OLD.description, OLD.status, OLD.resolved_by,
               OLD.resolution, OLD.created_at,
               tstzrange(lower(OLD.sys_period), NOW());
        NEW.sys_period = tstzrange(NOW(), NULL);
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO moderation.abuse_reports_history
        SELECT OLD.id, OLD.content_type, OLD.content_id, OLD.reporter_id,
               OLD.reason, OLD.description, OLD.status, OLD.resolved_by,
               OLD.resolution, OLD.created_at,
               tstzrange(lower(OLD.sys_period), NOW());
        RETURN OLD;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Attach trigger
CREATE TRIGGER abuse_reports_versioning
    BEFORE UPDATE OR DELETE ON moderation.abuse_reports
    FOR EACH ROW EXECUTE FUNCTION moderation.abuse_reports_versioning_trigger();

-- Indexes
CREATE INDEX idx_abuse_reports_status ON moderation.abuse_reports(status);
CREATE INDEX idx_abuse_reports_content_id ON moderation.abuse_reports(content_id);
CREATE INDEX idx_abuse_reports_reporter_id ON moderation.abuse_reports(reporter_id);
