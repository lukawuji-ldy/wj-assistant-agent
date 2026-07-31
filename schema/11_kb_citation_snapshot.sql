-- 知识引用不可变快照
CREATE TABLE IF NOT EXISTS kb_citation_snapshot
(
    id               BIGINT        PRIMARY KEY,
    message_id       VARCHAR(64),
    ticket_id        VARCHAR(64),
    user_id          VARCHAR(64)   NOT NULL,
    doc_id           VARCHAR(64)   NOT NULL,
    version_id       BIGINT        NOT NULL,
    version          VARCHAR(32)   NOT NULL,
    section          VARCHAR(500),
    chunk_id         VARCHAR(128)  NOT NULL,
    score            NUMERIC(6, 4),
    content_snapshot TEXT          NOT NULL,
    content_hash     VARCHAR(64)   NOT NULL,
    cited_at         TIMESTAMPTZ   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_citation_message ON kb_citation_snapshot (message_id);
CREATE INDEX IF NOT EXISTS idx_citation_ticket ON kb_citation_snapshot (ticket_id);
CREATE INDEX IF NOT EXISTS idx_citation_doc_version ON kb_citation_snapshot (doc_id, version_id);

COMMENT ON TABLE kb_citation_snapshot IS '知识引用不可变快照（历史不可被当前 ACTIVE 覆盖）';
COMMENT ON COLUMN kb_citation_snapshot.id IS '主键';
COMMENT ON COLUMN kb_citation_snapshot.message_id IS '关联消息';
COMMENT ON COLUMN kb_citation_snapshot.ticket_id IS '关联工单（可选）';
COMMENT ON COLUMN kb_citation_snapshot.user_id IS '用户';
COMMENT ON COLUMN kb_citation_snapshot.doc_id IS '文档业务键';
COMMENT ON COLUMN kb_citation_snapshot.version_id IS '版本主键';
COMMENT ON COLUMN kb_citation_snapshot.version IS '版本号';
COMMENT ON COLUMN kb_citation_snapshot.section IS '章节路径';
COMMENT ON COLUMN kb_citation_snapshot.chunk_id IS '片段 ID';
COMMENT ON COLUMN kb_citation_snapshot.score IS '当时命中得分';
COMMENT ON COLUMN kb_citation_snapshot.content_snapshot IS '当时片段正文快照';
COMMENT ON COLUMN kb_citation_snapshot.content_hash IS '正文哈希';
COMMENT ON COLUMN kb_citation_snapshot.cited_at IS '引用时间';
