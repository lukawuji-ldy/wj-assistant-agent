-- Chunk Revision + Embedding Set：建表、从 vector_store 回填、调整 citation
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- >>> kb_chunk
CREATE TABLE IF NOT EXISTS kb_chunk
(
    chunk_id          UUID         PRIMARY KEY,
    version_id        BIGINT       NOT NULL,
    doc_id            VARCHAR(64)  NOT NULL,
    collection        VARCHAR(64)  NOT NULL,
    chunk_seq         INTEGER      NOT NULL,
    chunk_key         VARCHAR(128),
    current_revision  INTEGER      NOT NULL,
    section           VARCHAR(500),
    summary           VARCHAR(1000),
    status            VARCHAR(20)  NOT NULL,
    create_time       TIMESTAMPTZ  NOT NULL,
    update_time       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_kb_chunk_version_seq UNIQUE (version_id, chunk_seq)
);

CREATE INDEX IF NOT EXISTS idx_kb_chunk_doc_version
    ON kb_chunk (doc_id, version_id, status);

CREATE INDEX IF NOT EXISTS idx_kb_chunk_version_seq
    ON kb_chunk (version_id, chunk_seq);

-- >>> kb_chunk_revision
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

-- >>> kb_embedding_set
CREATE TABLE IF NOT EXISTS kb_embedding_set
(
    id                       BIGINT       PRIMARY KEY,
    version_id               BIGINT       NOT NULL,
    embedding_config_id      VARCHAR(64)  NOT NULL,
    embedding_model_version  VARCHAR(256) NOT NULL,
    status                   VARCHAR(20)  NOT NULL,
    create_time              TIMESTAMPTZ  NOT NULL,
    update_time              TIMESTAMPTZ  NOT NULL,
    activated_at             TIMESTAMPTZ,
    deprecated_at            TIMESTAMPTZ,
    CONSTRAINT uk_kb_embedding_set_version_model
        UNIQUE (version_id, embedding_model_version)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_embedding_one_active
    ON kb_embedding_set (version_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_kb_embedding_set_status
    ON kb_embedding_set (version_id, status);

-- >>> kb_chunk_embedding（维度与现网 vector_store.embedding 对齐，避免 1024/1536 冲突）
DO $$
DECLARE
    dims INTEGER;
    model_fp TEXT;
BEGIN
    SELECT a.atttypmod
      INTO dims
    FROM pg_attribute a
    WHERE a.attrelid = 'vector_store'::regclass
      AND a.attname = 'embedding'
      AND a.attnum > 0
      AND NOT a.attisdropped;

    IF dims IS NULL OR dims <= 0 THEN
        dims := 1536;
    END IF;

    model_fp := 'migrated|unknown|' || dims::text;

    IF to_regclass('public.kb_chunk_embedding') IS NULL THEN
        EXECUTE format($sql$
            CREATE TABLE kb_chunk_embedding
            (
                set_id            BIGINT       NOT NULL,
                chunk_id          UUID         NOT NULL,
                embedding         VECTOR(%s)   NOT NULL,
                content_revision  INTEGER      NOT NULL,
                content_hash      VARCHAR(64)  NOT NULL,
                create_time       TIMESTAMPTZ  NOT NULL,
                update_time       TIMESTAMPTZ  NOT NULL,
                PRIMARY KEY (set_id, chunk_id),
                CONSTRAINT fk_kb_chunk_embedding_set
                    FOREIGN KEY (set_id) REFERENCES kb_embedding_set (id),
                CONSTRAINT fk_kb_chunk_embedding_chunk
                    FOREIGN KEY (chunk_id) REFERENCES kb_chunk (chunk_id)
            )
        $sql$, dims);
    END IF;

    CREATE INDEX IF NOT EXISTS idx_kb_chunk_embedding_chunk
        ON kb_chunk_embedding (chunk_id);

    BEGIN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_kb_chunk_embedding_vector
            ON kb_chunk_embedding USING ivfflat (embedding vector_cosine_ops)
            WITH (lists = 100)';
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'skip idx_kb_chunk_embedding_vector: %', SQLERRM;
    END;

    -- 回填 kb_chunk（chunk_id = vector_store.id；chunk_seq 用窗口去重）
    INSERT INTO kb_chunk (
        chunk_id, version_id, doc_id, collection, chunk_seq, chunk_key,
        current_revision, section, summary, status, create_time, update_time
    )
    SELECT
        ranked.id,
        ranked.version_id,
        ranked.doc_id,
        ranked.collection,
        ranked.chunk_seq,
        ranked.chunk_key,
        1,
        ranked.section,
        ranked.summary,
        ranked.status,
        ranked.create_time,
        ranked.update_time
    FROM (
        SELECT
            vs.id,
            (vs.metadata ->> 'version_id')::bigint AS version_id,
            COALESCE(vs.metadata ->> 'doc_id', '') AS doc_id,
            COALESCE(vs.metadata ->> 'collection', 'kb_default') AS collection,
            ROW_NUMBER() OVER (
                PARTITION BY (vs.metadata ->> 'version_id')
                ORDER BY COALESCE(vs.chunk_seq, 2147483647), vs.ingested_at NULLS LAST, vs.id
            ) AS chunk_seq,
            vs.metadata ->> 'chunk_id' AS chunk_key,
            NULLIF(vs.metadata ->> 'section', '') AS section,
            NULLIF(vs.metadata ->> 'summary', '') AS summary,
            CASE
                WHEN COALESCE(vs.metadata ->> 'status', 'ACTIVE') = 'DEPRECATED' THEN 'DEPRECATED'
                ELSE 'ACTIVE'
            END AS status,
            COALESCE(vs.ingested_at, NOW()) AS create_time,
            COALESCE(vs.ingested_at, NOW()) AS update_time
        FROM vector_store vs
        WHERE vs.metadata ->> 'version_id' ~ '^[0-9]+$'
    ) ranked
    WHERE NOT EXISTS (SELECT 1 FROM kb_chunk c WHERE c.chunk_id = ranked.id)
    ON CONFLICT (chunk_id) DO NOTHING;

    -- 回填 revision=1
    INSERT INTO kb_chunk_revision (chunk_id, revision, content, content_hash, status, create_time)
    SELECT
        c.chunk_id,
        1,
        COALESCE(vs.content, ''),
        encode(digest(COALESCE(vs.content, ''), 'sha256'), 'hex'),
        'ACTIVE',
        c.create_time
    FROM kb_chunk c
    JOIN vector_store vs ON vs.id = c.chunk_id
    WHERE NOT EXISTS (
        SELECT 1 FROM kb_chunk_revision r WHERE r.chunk_id = c.chunk_id AND r.revision = 1
    );

    -- 每 version 一个 embedding set（指纹含实际维度）
    INSERT INTO kb_embedding_set (
        id, version_id, embedding_config_id, embedding_model_version,
        status, create_time, update_time, activated_at, deprecated_at
    )
    SELECT
        (('x' || substr(md5(v.id::text), 1, 15))::bit(60)::bigint),
        v.id,
        'migrated',
        model_fp,
        CASE WHEN v.status = 'ACTIVE' THEN 'ACTIVE' ELSE 'DEPRECATED' END,
        NOW(),
        NOW(),
        CASE WHEN v.status = 'ACTIVE' THEN NOW() ELSE NULL END,
        CASE WHEN v.status = 'ACTIVE' THEN NULL ELSE NOW() END
    FROM kb_document_version v
    WHERE EXISTS (
        SELECT 1 FROM kb_chunk c WHERE c.version_id = v.id
    )
    ON CONFLICT (version_id, embedding_model_version) DO NOTHING;

    -- 仅拷贝维度与目标列一致的向量
    EXECUTE format($sql$
        INSERT INTO kb_chunk_embedding (
            set_id, chunk_id, embedding, content_revision, content_hash, create_time, update_time
        )
        SELECT
            s.id,
            c.chunk_id,
            vs.embedding,
            1,
            r.content_hash,
            NOW(),
            NOW()
        FROM kb_chunk c
        JOIN kb_chunk_revision r ON r.chunk_id = c.chunk_id AND r.revision = 1
        JOIN kb_embedding_set s ON s.version_id = c.version_id
            AND s.embedding_model_version = %L
        JOIN vector_store vs ON vs.id = c.chunk_id
        WHERE vs.embedding IS NOT NULL
          AND vector_dims(vs.embedding) = %s
        ON CONFLICT (set_id, chunk_id) DO NOTHING
    $sql$, model_fp, dims);
END $$;

-- citation：加 revision；chunk_id VARCHAR → UUID
ALTER TABLE kb_citation_snapshot
    ADD COLUMN IF NOT EXISTS revision INTEGER;

UPDATE kb_citation_snapshot
SET revision = 1
WHERE revision IS NULL;

ALTER TABLE kb_citation_snapshot
    ALTER COLUMN revision SET DEFAULT 1;

ALTER TABLE kb_citation_snapshot
    ALTER COLUMN revision SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'kb_citation_snapshot'
          AND column_name = 'chunk_id'
          AND data_type = 'character varying'
    ) THEN
        ALTER TABLE kb_citation_snapshot ADD COLUMN IF NOT EXISTS chunk_id_uuid UUID;
        UPDATE kb_citation_snapshot
        SET chunk_id_uuid = chunk_id::uuid
        WHERE chunk_id_uuid IS NULL
          AND chunk_id ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$';
        UPDATE kb_citation_snapshot
        SET chunk_id_uuid = gen_random_uuid()
        WHERE chunk_id_uuid IS NULL;
        ALTER TABLE kb_citation_snapshot DROP COLUMN chunk_id;
        ALTER TABLE kb_citation_snapshot RENAME COLUMN chunk_id_uuid TO chunk_id;
        ALTER TABLE kb_citation_snapshot ALTER COLUMN chunk_id SET NOT NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_citation_chunk ON kb_citation_snapshot (chunk_id, revision);
