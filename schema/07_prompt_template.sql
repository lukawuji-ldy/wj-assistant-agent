-- 提示词模板
CREATE TABLE IF NOT EXISTS prompt_template
(
    id           BIGINT       PRIMARY KEY,
    code         VARCHAR(128) NOT NULL,
    name         VARCHAR(128) NOT NULL,
    role         VARCHAR(20)  NOT NULL,
    content      TEXT         NOT NULL,
    version      INT          NOT NULL DEFAULT 1,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    create_time  TIMESTAMPTZ  NOT NULL,
    update_time  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_prompt_code_ver UNIQUE (code, version)
);

CREATE INDEX IF NOT EXISTS idx_prompt_code_active ON prompt_template (code, status);

COMMENT ON TABLE prompt_template IS '系统/用户提示词模板（配置化管理）';
COMMENT ON COLUMN prompt_template.id IS '主键';
COMMENT ON COLUMN prompt_template.code IS '模板编码，如 agent.default.system';
COMMENT ON COLUMN prompt_template.name IS '模板名称';
COMMENT ON COLUMN prompt_template.role IS 'SYSTEM|USER';
COMMENT ON COLUMN prompt_template.content IS '模板正文，可含变量占位';
COMMENT ON COLUMN prompt_template.version IS '版本号';
COMMENT ON COLUMN prompt_template.status IS 'ACTIVE/DISABLED';
COMMENT ON COLUMN prompt_template.create_time IS '创建时间';
COMMENT ON COLUMN prompt_template.update_time IS '更新时间';
