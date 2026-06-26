-- Tenant isolation via row-level security. The app runs as the non-owner "app_rls" role
-- (subject to RLS); migrations run as the owner/superuser (bypasses RLS).

-- Least-privilege app login role (idempotent).
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_rls') THEN
        CREATE ROLE app_rls LOGIN PASSWORD 'app_rls_pass';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO app_rls;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_rls;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_rls;

-- Grant the app role access to tables/sequences created by future migrations too.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_rls;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO app_rls;

-- Enable RLS and scope every row to the current tenant. The "true" in current_setting makes
-- an unset tenant return NULL (matches no rows = default deny) instead of erroring.
ALTER TABLE tasks ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_tasks ON tasks
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_users ON users
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
