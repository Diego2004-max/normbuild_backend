CREATE TABLE IF NOT EXISTS regulatory_documents (
    id UUID PRIMARY KEY,
    title VARCHAR(240) NOT NULL,
    jurisdiction VARCHAR(120) NOT NULL,
    regulation_code VARCHAR(80) NOT NULL,
    article_reference VARCHAR(120) NOT NULL,
    source_url TEXT,
    content TEXT NOT NULL,
    embedding vector(768) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE regulatory_documents
    ADD COLUMN IF NOT EXISTS source_url TEXT;

CREATE INDEX IF NOT EXISTS idx_regulatory_documents_embedding
    ON regulatory_documents USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE INDEX IF NOT EXISTS idx_regulatory_documents_jurisdiction
    ON regulatory_documents (jurisdiction);
