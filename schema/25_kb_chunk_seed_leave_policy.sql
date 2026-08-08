-- =============================================================================
-- 手工补齐 leave policy chunk（向量可空，走 ILIKE）
-- 用法：
--   psql "postgresql://postgres:PASSWORD@127.0.0.1:5432/vector_test" \
--     -f schema/25_kb_chunk_seed_leave_policy.sql
-- 正常开发路径：启动 assistant-agent-server，由 DevSeedRunner.ensureLeavePolicyChunks 写入。
-- =============================================================================

DO $$
DECLARE
    c1 TEXT := 'Employees may take annual leave up to 15 days per year. Submit requests in HR portal at least 3 days in advance.';
    c2 TEXT := 'Sick leave requires a medical certificate when absence exceeds 2 consecutive working days.';
    h1 TEXT;
    h2 TEXT;
    id1 UUID := 'aaaaaaaa-bbbb-cccc-dddd-111111111111';
    id2 UUID := 'aaaaaaaa-bbbb-cccc-dddd-222222222222';
BEGIN
    IF to_regclass('public.kb_chunk') IS NULL THEN
        RAISE NOTICE 'kb_chunk missing — run Flyway V15/V16 first';
        RETURN;
    END IF;

    IF EXISTS (SELECT 1 FROM kb_chunk WHERE doc_id = 'doc_leave_policy' AND version_id = 1001) THEN
        RAISE NOTICE 'leave policy chunks already present';
        RETURN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM kb_document WHERE doc_id = 'doc_leave_policy') THEN
        INSERT INTO kb_document
        (id, doc_id, collection, title, current_version_id, create_time, update_time)
        VALUES (100, 'doc_leave_policy', 'kb_default', 'Employee Leave Policy', 1001, NOW(), NOW());
    END IF;

    IF NOT EXISTS (SELECT 1 FROM kb_document_version WHERE id = 1001) THEN
        INSERT INTO kb_document_version
        (id, doc_id, version, status, source, acl_roles, published_at, create_time, update_time)
        VALUES (1001, 'doc_leave_policy', 'v1', 'ACTIVE', 'seed://leave-policy',
                '["admin","user"]'::jsonb, NOW(), NOW(), NOW());
    END IF;

    h1 := encode(digest(c1, 'sha256'), 'hex');
    h2 := encode(digest(c2, 'sha256'), 'hex');

    INSERT INTO kb_chunk
    (chunk_id, version_id, doc_id, collection, chunk_seq, chunk_key,
     current_revision, section, summary, status, create_time, update_time)
    VALUES
        (id1, 1001, 'doc_leave_policy', 'kb_default', 1, 'doc_leave_policy_v1_c1',
         1, 'annual', left(c1, 80), 'ACTIVE', NOW(), NOW()),
        (id2, 1001, 'doc_leave_policy', 'kb_default', 2, 'doc_leave_policy_v1_c2',
         1, 'sick', left(c2, 80), 'ACTIVE', NOW(), NOW())
    ON CONFLICT (chunk_id) DO NOTHING;

    INSERT INTO kb_chunk_revision (chunk_id, revision, content, content_hash, status, create_time)
    VALUES
        (id1, 1, c1, h1, 'ACTIVE', NOW()),
        (id2, 1, c2, h2, 'ACTIVE', NOW())
    ON CONFLICT (chunk_id, revision) DO NOTHING;

    RAISE NOTICE 'leave policy kb_chunk seed applied';
END $$;
