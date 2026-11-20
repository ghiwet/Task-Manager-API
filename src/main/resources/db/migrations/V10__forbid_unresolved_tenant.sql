-- Threat #52: '__unresolved__' is the sentinel TenantContext binds when no tenant is resolved. RLS
-- makes it default-deny for reads of real tenants, but its WITH CHECK would still accept inserts
-- stamped with the sentinel, turning it into one shared, writable tenant for every tenant-less
-- caller. TenantFilter now rejects such API requests; this is the database backstop, so no code
-- path (background job, startup runner, future endpoint) can ever write a row into it.
--
-- NOT VALID: enforced for every new or updated row, without failing the migration on a dev DB that
-- already holds sentinel rows (e.g. from the old startup user seeding). Those rows stay unreachable:
-- no request can bind the sentinel tenant any more.

ALTER TABLE tasks
    ADD CONSTRAINT chk_tasks_tenant_resolved CHECK (tenant_id <> '__unresolved__') NOT VALID;
ALTER TABLE users
    ADD CONSTRAINT chk_users_tenant_resolved CHECK (tenant_id <> '__unresolved__') NOT VALID;
ALTER TABLE audit_log
    ADD CONSTRAINT chk_audit_log_tenant_resolved CHECK (tenant_id <> '__unresolved__') NOT VALID;
ALTER TABLE vector_store
    ADD CONSTRAINT chk_vector_store_tenant_resolved CHECK (tenant_id <> '__unresolved__') NOT VALID;
