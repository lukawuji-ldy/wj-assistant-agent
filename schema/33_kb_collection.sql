-- 知识库命名空间说明，供多库 Embedding 路由。collection 与 kb_document.collection 相同。
CREATE TABLE IF NOT EXISTS kb_collection
(
    collection  VARCHAR(64) PRIMARY KEY,
    description TEXT        NOT NULL,
    update_time TIMESTAMPTZ NOT NULL
);

COMMENT ON TABLE kb_collection IS '知识库（collection）路由说明；wuji.rag.router.enabled=false 时不参与检索';
COMMENT ON COLUMN kb_collection.collection IS '知识库命名空间，对齐 kb_document.collection / kb_chunk.collection';
COMMENT ON COLUMN kb_collection.description IS '供查询向量做余弦选库的说明';
COMMENT ON COLUMN kb_collection.update_time IS '说明更新时间';
