-- 知识库 chunk：同版本内序号 + 入库时间（毫秒精度），便于管理台按生成顺序查看
ALTER TABLE vector_store
    ADD COLUMN IF NOT EXISTS chunk_seq INTEGER;

ALTER TABLE vector_store
    ADD COLUMN IF NOT EXISTS ingested_at TIMESTAMPTZ(3);

COMMENT ON COLUMN vector_store.chunk_seq IS '同 version 内切分序号（1-based）；管理台默认按此排序';
COMMENT ON COLUMN vector_store.ingested_at IS '入库时间，精度到毫秒';

-- 历史数据：从 metadata.chunk_id 后缀 _cN 回填序号
UPDATE vector_store
SET chunk_seq = CASE
                    WHEN metadata ? 'chunk_seq'
                        AND (metadata ->> 'chunk_seq') ~ '^[0-9]+$'
                        THEN (metadata ->> 'chunk_seq')::INTEGER
                    WHEN metadata ->> 'chunk_id' ~ '_c[0-9]+$'
                        THEN substring(metadata ->> 'chunk_id' FROM '_c([0-9]+)$')::INTEGER
                    ELSE NULL
    END
WHERE chunk_seq IS NULL;

-- 历史数据：从 metadata.ingested_at 回填
UPDATE vector_store
SET ingested_at = (metadata ->> 'ingested_at')::TIMESTAMPTZ
WHERE ingested_at IS NULL
  AND metadata ? 'ingested_at'
  AND (metadata ->> 'ingested_at') IS NOT NULL
  AND (metadata ->> 'ingested_at') <> '';

-- 列值回写 metadata，检索侧可见
UPDATE vector_store
SET metadata = metadata
    || CASE WHEN chunk_seq IS NOT NULL THEN jsonb_build_object('chunk_seq', chunk_seq) ELSE '{}'::jsonb END
    || CASE
           WHEN ingested_at IS NOT NULL
               THEN jsonb_build_object('ingested_at', to_char(ingested_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'))
           ELSE '{}'::jsonb
    END
WHERE chunk_seq IS NOT NULL
   OR ingested_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_vector_store_version_seq
    ON vector_store ((metadata ->> 'version_id'), chunk_seq);
