-- Multi-tenancy: add tenant_id to tenant-scoped tables.
-- DEFAULT 'default' backfills existing rows and keeps existing inserts working.

ALTER TABLE tasks ADD COLUMN tenant_id VARCHAR(255) NOT NULL DEFAULT 'default';
ALTER TABLE users ADD COLUMN tenant_id VARCHAR(255) NOT NULL DEFAULT 'default';

CREATE INDEX idx_tasks_tenant_id ON tasks (tenant_id);
CREATE INDEX idx_users_tenant_id ON users (tenant_id);
