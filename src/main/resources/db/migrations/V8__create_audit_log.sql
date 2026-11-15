-- Append-only audit trail for admin destructive actions (threat #2). The REVOKE below overrides
-- V5's blanket default-privilege grant, so app_rls keeps only INSERT + SELECT.
CREATE TABLE audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(100) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    trace_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

REVOKE UPDATE, DELETE ON audit_log FROM app_rls;

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_audit_log ON audit_log
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
