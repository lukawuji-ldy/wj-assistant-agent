-- 知识库逻辑片段（跨 revision 稳定）；当前激活向量挂本表
CREATE TABLE IF NOT EXISTS kb_chunk
(
    chunk_id                    UUID         PRIMARY KEY,
    version_id                  BIGINT       NOT NULL,
    doc_id                      VARCHAR(64)  NOT NULL,
    collection                  VARCHAR(64)  NOT NULL,
    chunk_seq                   INTEGER      NOT NULL,
    chunk_key                   VARCHAR(128),
    current_revision            INTEGER      NOT NULL,
    section                     VARCHAR(500),
    summary                     VARCHAR(1000),
    status                      VARCHAR(20)  NOT NULL,
    embedding                   VECTOR(1536),
    embedding_content_revision  INTEGER,
    embedding_content_hash      VARCHAR(64),
    create_time                 TIMESTAMPTZ  NOT NULL,
    update_time                 TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_kb_chunk_version_seq UNIQUE (version_id, chunk_seq)
);

CREATE INDEX IF NOT EXISTS idx_kb_chunk_doc_version
    ON kb_chunk (doc_id, version_id, status);

CREATE INDEX IF NOT EXISTS idx_kb_chunk_version_seq
    ON kb_chunk (version_id, chunk_seq);

COMMENT ON TABLE kb_chunk IS '知识库逻辑片段（跨 revision 稳定）；当前激活向量挂本表';
COMMENT ON COLUMN kb_chunk.chunk_id IS '稳定逻辑 UUID；API / 引用 / 回滚目标';
COMMENT ON COLUMN kb_chunk.chunk_key IS '可选展示键';
COMMENT ON COLUMN kb_chunk.current_revision IS '当前 ACTIVE revision 号';
COMMENT ON COLUMN kb_chunk.status IS 'ACTIVE / DEPRECATED';
COMMENT ON COLUMN kb_chunk.embedding IS '当前激活向量，可空';
COMMENT ON COLUMN kb_chunk.embedding_content_revision IS '生成该向量时的 ACTIVE revision';
COMMENT ON COLUMN kb_chunk.embedding_content_hash IS '生成时正文 hash';
