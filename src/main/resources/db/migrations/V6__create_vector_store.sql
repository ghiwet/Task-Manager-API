-- pgvector store for the AI assistant (RAG over task descriptions).
-- Schema matches Spring AI's PgVectorStore (table vector_store; columns id/content/metadata/embedding)
-- so we manage it via Flyway with schema init disabled. embedding is 384-dim (all-MiniLM ONNX model).
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE vector_store (
    id        uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    content   text,
    metadata  json,
    embedding vector(384),
    -- Tenant comes from the app.current_tenant GUC, which the application pins per transaction with
    -- SET LOCAL before each vector-store write (Spring AI inserts are tenant-agnostic); an unset GUC
    -- yields no tenant and the row is rejected by the RLS policy below (safe).
    tenant_id varchar(255) NOT NULL DEFAULT current_setting('app.current_tenant', true)
);

CREATE INDEX idx_vector_store_embedding ON vector_store USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_vector_store_tenant ON vector_store (tenant_id);

-- Tenant isolation at the database layer, same model as tasks/users (see V5).
ALTER TABLE vector_store ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_vector_store ON vector_store
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

GRANT SELECT, INSERT, UPDATE, DELETE ON vector_store TO app_rls;
