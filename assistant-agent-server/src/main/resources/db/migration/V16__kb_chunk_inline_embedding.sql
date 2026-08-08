-- 向量并入 kb_chunk；模型指纹并入 kb_document_version；回填后 DROP set/embedding 表
ALTER TABLE kb_document_version
    ADD COLUMN IF NOT EXISTS embedding_config_id VARCHAR(64);
ALTER TABLE kb_document_version
    ADD COLUMN IF NOT EXISTS embedding_model_version VARCHAR(256);

DO $$
DECLARE
    dims INTEGER;
BEGIN
    -- 维度：优先 kb_chunk_embedding，其次 vector_store，否则 1536
    IF to_regclass('public.kb_chunk_embedding') IS NOT NULL THEN
        SELECT a.atttypmod
          INTO dims
        FROM pg_attribute a
        WHERE a.attrelid = 'kb_chunk_embedding'::regclass
          AND a.attname = 'embedding'
          AND a.attnum > 0
          AND NOT a.attisdropped;
    END IF;

    IF dims IS NULL OR dims <= 0 THEN
        IF to_regclass('public.vector_store') IS NOT NULL THEN
            SELECT a.atttypmod
              INTO dims
            FROM pg_attribute a
            WHERE a.attrelid = 'vector_store'::regclass
              AND a.attname = 'embedding'
              AND a.attnum > 0
              AND NOT a.attisdropped;
        END IF;
    END IF;

    IF dims IS NULL OR dims <= 0 THEN
        dims := 1536;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'kb_chunk' AND column_name = 'embedding'
    ) THEN
        EXECUTE format('ALTER TABLE kb_chunk ADD COLUMN embedding VECTOR(%s)', dims);
    END IF;
END $$;

ALTER TABLE kb_chunk
    ADD COLUMN IF NOT EXISTS embedding_content_revision INTEGER;
ALTER TABLE kb_chunk
    ADD COLUMN IF NOT EXISTS embedding_content_hash VARCHAR(64);

-- 从 ACTIVE set 回填（表不存在则跳过）
DO $$
BEGIN
    IF to_regclass('public.kb_embedding_set') IS NOT NULL THEN
        UPDATE kb_document_version v
        SET embedding_config_id = s.embedding_config_id,
            embedding_model_version = s.embedding_model_version
        FROM kb_embedding_set s
        WHERE s.version_id = v.id
          AND s.status = 'ACTIVE';
    END IF;

    IF to_regclass('public.kb_chunk_embedding') IS NOT NULL
       AND to_regclass('public.kb_embedding_set') IS NOT NULL THEN
        UPDATE kb_chunk c
        SET embedding = e.embedding,
            embedding_content_revision = e.content_revision,
            embedding_content_hash = e.content_hash,
            update_time = NOW()
        FROM kb_chunk_embedding e
        JOIN kb_embedding_set s ON s.id = e.set_id AND s.status = 'ACTIVE'
        WHERE e.chunk_id = c.chunk_id;
    END IF;
END $$;

DROP TABLE IF EXISTS kb_chunk_embedding;
DROP TABLE IF EXISTS kb_embedding_set;

DO $$
BEGIN
    CREATE INDEX IF NOT EXISTS idx_kb_chunk_embedding
        ON kb_chunk USING ivfflat (embedding vector_cosine_ops)
        WITH (lists = 100);
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'skip idx_kb_chunk_embedding: %', SQLERRM;
END $$;

COMMENT ON COLUMN kb_document_version.embedding_config_id IS '当前该版本向量所用 llm_config.config_id';
COMMENT ON COLUMN kb_document_version.embedding_model_version IS '指纹 config_id|model|dimensions';
COMMENT ON COLUMN kb_chunk.embedding IS '当前激活向量，可空';
COMMENT ON COLUMN kb_chunk.embedding_content_revision IS '生成该向量时的 ACTIVE revision';
COMMENT ON COLUMN kb_chunk.embedding_content_hash IS '生成时正文 hash';
