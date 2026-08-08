-- V12: prompt_template 每 code 一行线上副本 + prompt_template_version 全量历史（草稿/发布/回滚）

CREATE TABLE IF NOT EXISTS prompt_template_version
(
    id           BIGINT       PRIMARY KEY,
    code         VARCHAR(128) NOT NULL,
    version      INT          NOT NULL,
    name         VARCHAR(128) NOT NULL,
    role         VARCHAR(20)  NOT NULL,
    content      TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    change_note  VARCHAR(512),
    created_by   VARCHAR(64)  NOT NULL,
    create_time  TIMESTAMPTZ  NOT NULL,
    publish_time TIMESTAMPTZ,
    CONSTRAINT uk_prompt_ver_code_ver UNIQUE (code, version)
);

CREATE INDEX IF NOT EXISTS idx_prompt_ver_code_status ON prompt_template_version (code, status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_prompt_ver_one_draft ON prompt_template_version (code) WHERE status = 'DRAFT';

COMMENT ON TABLE prompt_template_version IS '提示词版本历史（DRAFT/PUBLISHED/SUPERSEDED）';
COMMENT ON COLUMN prompt_template_version.status IS 'DRAFT|PUBLISHED|SUPERSEDED';
COMMENT ON COLUMN prompt_template_version.change_note IS '变更说明';
COMMENT ON COLUMN prompt_template_version.created_by IS '操作者 admin_id；迁移种子为 system';
COMMENT ON COLUMN prompt_template_version.publish_time IS '发布时间；草稿为 NULL';

-- 将旧多行迁入版本表：各 code 的 ACTIVE 最大 version → PUBLISHED；无 ACTIVE 则取最大 version → PUBLISHED；其余 SUPERSEDED
INSERT INTO prompt_template_version
    (id, code, version, name, role, content, status, change_note, created_by, create_time, publish_time)
SELECT
    p.id,
    p.code,
    p.version,
    p.name,
    p.role,
    p.content,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM prompt_template a
            WHERE a.code = p.code AND a.status = 'ACTIVE'
        )
            AND p.status = 'ACTIVE'
            AND p.version = (
                SELECT MAX(t.version) FROM prompt_template t
                WHERE t.code = p.code AND t.status = 'ACTIVE'
            )
            THEN 'PUBLISHED'
        WHEN NOT EXISTS (
            SELECT 1 FROM prompt_template a
            WHERE a.code = p.code AND a.status = 'ACTIVE'
        )
            AND p.version = (
                SELECT MAX(t.version) FROM prompt_template t WHERE t.code = p.code
            )
            THEN 'PUBLISHED'
        ELSE 'SUPERSEDED'
    END,
    'migrated',
    'system',
    p.create_time,
    p.update_time
FROM prompt_template p
WHERE NOT EXISTS (
    SELECT 1 FROM prompt_template_version v WHERE v.code = p.code AND v.version = p.version
);

-- 主表只保留每 code 的 PUBLISHED 对应行
DELETE FROM prompt_template p
WHERE NOT EXISTS (
    SELECT 1 FROM prompt_template_version v
    WHERE v.id = p.id AND v.status = 'PUBLISHED'
);

ALTER TABLE prompt_template DROP CONSTRAINT IF EXISTS uk_prompt_code_ver;

ALTER TABLE prompt_template RENAME COLUMN version TO published_version;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_prompt_code'
    ) THEN
        ALTER TABLE prompt_template ADD CONSTRAINT uk_prompt_code UNIQUE (code);
    END IF;
END $$;

COMMENT ON TABLE prompt_template IS '提示词线上副本（每 code 一行）';
COMMENT ON COLUMN prompt_template.published_version IS '当前已发布版本号，对应 prompt_template_version.version';
COMMENT ON COLUMN prompt_template.status IS 'ACTIVE/DISABLED（整条 code 是否对热路径可见）';
COMMENT ON COLUMN prompt_template.update_time IS '主表最近发布时间';
