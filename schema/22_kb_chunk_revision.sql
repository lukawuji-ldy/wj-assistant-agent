-- 知识库片段正文 revision 历史
CREATE TABLE IF NOT EXISTS kb_chunk_revision
(
    chunk_id     UUID         NOT NULL,
    revision     INTEGER      NOT NULL,
    content      TEXT         NOT NULL,
    content_hash VARCHAR(64)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_by   VARCHAR(64),
    create_time  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (chunk_id, revision),
    CONSTRAINT fk_kb_chunk_revision_chunk
        FOREIGN KEY (chunk_id) REFERENCES kb_chunk (chunk_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_chunk_one_active_revision
    ON kb_chunk_revision (chunk_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_kb_chunk_revision_hash
    ON kb_chunk_revision (chunk_id, content_hash);

COMMENT ON TABLE kb_chunk_revision IS '知识库片段正文 revision（旧版 DEPRECATED 保留）';
COMMENT ON COLUMN kb_chunk_revision.content_hash IS '正文 SHA-256 hex';
COMMENT ON COLUMN kb_chunk_revision.status IS '同 chunk 同时最多一个 ACTIVE';
