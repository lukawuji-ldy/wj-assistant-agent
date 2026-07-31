-- 企业知识库逻辑文档
CREATE TABLE IF NOT EXISTS kb_document
(
    id                 BIGINT       PRIMARY KEY,
    doc_id             VARCHAR(64)  NOT NULL,
    collection         VARCHAR(64)  NOT NULL,
    title              VARCHAR(500) NOT NULL,
    current_version_id BIGINT,
    create_time        TIMESTAMPTZ  NOT NULL,
    update_time        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_kb_doc_id UNIQUE (doc_id)
);

CREATE INDEX IF NOT EXISTS idx_kb_document_collection
    ON kb_document (collection);

COMMENT ON TABLE kb_document IS '企业知识库逻辑文档';
COMMENT ON COLUMN kb_document.id IS '主键';
COMMENT ON COLUMN kb_document.doc_id IS '逻辑文档业务键';
COMMENT ON COLUMN kb_document.collection IS '知识库命名空间';
COMMENT ON COLUMN kb_document.title IS '文档标题';
COMMENT ON COLUMN kb_document.current_version_id IS '当前 ACTIVE 版本主键';
COMMENT ON COLUMN kb_document.create_time IS '创建时间';
COMMENT ON COLUMN kb_document.update_time IS '更新时间';
