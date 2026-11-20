-- Threat #52: no rows in the '__unresolved__' sentinel tenant (DB backstop to TenantFilter's 403).
-- NOT VALID: checks new/updated rows only, so DBs with old sentinel rows still migrate.

ALTER TABLE tasks
    ADD CONSTRAINT chk_tasks_tenant_resolved CHECK (tenant_id <> '__unresolved__') NOT VALID;
ALTER TABLE users
    ADD CONSTRAINT chk_users_tenant_resolved CHECK (tenant_id <> '__unresolved__') NOT VALID;
ALTER TABLE audit_log
    ADD CONSTRAINT chk_audit_log_tenant_resolved CHECK (tenant_id <> '__unresolved__') NOT VALID;
ALTER TABLE vector_store
    ADD CONSTRAINT chk_vector_store_tenant_resolved CHECK (tenant_id <> '__unresolved__') NOT VALID;
