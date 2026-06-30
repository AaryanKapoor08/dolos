-- Spring AI PgVectorStore table (Phase 4B), owned by Flyway in ai-copilot-service's own `copilot`
-- schema. Spring AI's own schema initialization is disabled (spring.ai.vectorstore.pgvector.
-- initialize-schema=false), so it never auto-creates this table — Flyway is the single owner, matching
-- the house rule across every Dolos service.
--
-- The pgvector extension ships in the Postgres image (pgvector/pgvector:pg16). It is installed into
-- `public` (not the migration's `copilot` schema) so the `vector` type, its operators (<=>) and the
-- HNSW operator class resolve from any connection's search_path at query time — the runtime datasource
-- search_path is the default ($user, public), while Flyway runs with search_path = copilot.
CREATE EXTENSION IF NOT EXISTS vector SCHEMA public;

-- The columns PgVectorStore reads/writes: a uuid id (the store supplies it), the chunk text, JSON
-- metadata (e.g. the source document), and the embedding vector. nomic-embed-text emits 768 dimensions,
-- so the column width MUST be 768 — a mismatch fails only at insert time, not at boot.
CREATE TABLE IF NOT EXISTS copilot.vector_store (
    id        uuid PRIMARY KEY,
    content   text,
    metadata  json,
    embedding public.vector(768)
);

-- HNSW index for cosine-distance similarity search (matches distance-type=COSINE_DISTANCE). The
-- operator class is schema-qualified because the extension lives in public, not copilot.
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON copilot.vector_store USING hnsw (embedding public.vector_cosine_ops);
