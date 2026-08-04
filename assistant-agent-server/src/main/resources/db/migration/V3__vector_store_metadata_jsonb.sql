-- Spring AI / 早期手工建表可能把 metadata 建成 json；规范为 jsonb（与 schema/12_vector_store.sql 一致）
ALTER TABLE vector_store
    ALTER COLUMN metadata TYPE jsonb USING metadata::jsonb;
