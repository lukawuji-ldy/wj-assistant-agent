-- 知识库文档版本
CREATE TABLE IF NOT EXISTS kb_document_version
(
    id            BIGINT       PRIMARY KEY,
    doc_id        VARCHAR(64)  NOT NULL,
    version       VARCHAR(32)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    source        VARCHAR(500),
    acl_roles     JSONB        NOT NULL,
    published_at  TIMESTAMPTZ,
    deprecated_at TIMESTAMPTZ,
    create_time   TIMESTAMPTZ  NOT NULL,
    update_time   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_kb_doc_version UNIQUE (doc_id, version)
);

CREATE INDEX IF NOT EXISTS idx_kb_doc_version_status
    ON kb_document_version (doc_id, status);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_doc_one_active
    ON kb_document_version (doc_id)
    WHERE status = 'ACTIVE';

COMMENT ON TABLE kb_document_version IS '知识库文档版本（停用≠删除）';
COMMENT ON COLUMN kb_document_version.id IS '主键';
COMMENT ON COLUMN kb_document_version.doc_id IS '逻辑文档业务键';
COMMENT ON COLUMN kb_document_version.version IS '版本号，如 v3';
COMMENT ON COLUMN kb_document_version.status IS 'DRAFT|ACTIVE|DEPRECATED';
COMMENT ON COLUMN kb_document_version.source IS '来源文件或 URI';
COMMENT ON COLUMN kb_document_version.acl_roles IS '可见角色列表 JSON';
COMMENT ON COLUMN kb_document_version.published_at IS '发布时间';
COMMENT ON COLUMN kb_document_version.deprecated_at IS '停用时间';
COMMENT ON COLUMN kb_document_version.create_time IS '创建时间';
COMMENT ON COLUMN kb_document_version.update_time IS '更新时间';
