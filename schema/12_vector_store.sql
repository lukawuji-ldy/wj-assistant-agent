-- 企业知识库向量（Spring AI PGVector 兼容，维度默认 1536）
CREATE TABLE IF NOT EXISTS vector_store
(
    id        UUID PRIMARY KEY,
    content   TEXT,
    metadata  JSONB,
    embedding VECTOR(1536)
);

DO $$
BEGIN
    BEGIN
        CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
            ON vector_store USING ivfflat (embedding vector_cosine_ops)
            WITH (lists = 100);
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'skip idx_vector_store_embedding: %', SQLERRM;
    END;
END $$;

CREATE INDEX IF NOT EXISTS idx_vector_store_meta_doc
    ON vector_store ((metadata ->> 'doc_id'), (metadata ->> 'status'));

COMMENT ON TABLE vector_store IS '企业知识库向量切片表';
COMMENT ON COLUMN vector_store.id IS '切片主键 UUID';
COMMENT ON COLUMN vector_store.content IS '片段正文';
COMMENT ON COLUMN vector_store.metadata IS '元数据 JSON：doc_id/version/section/chunk_id 等';
COMMENT ON COLUMN vector_store.embedding IS '向量，维度须与 Embedding 模型一致';
