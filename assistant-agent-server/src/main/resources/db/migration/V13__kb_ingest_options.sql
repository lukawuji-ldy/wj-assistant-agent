-- P3：知识库版本入库参数（切分 / 源文件 / 解析器）
ALTER TABLE kb_document_version
    ADD COLUMN IF NOT EXISTS ingest_options JSONB;

COMMENT ON COLUMN kb_document_version.ingest_options IS
    '入库参数 JSON：chunkSize/overlap/minChunkLengthToKeep/chapterSplitEnabled/sourceFile/parser';
