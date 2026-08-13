-- VoiceTextAssistant（VTA）分析任务表
-- P0：仅支持四路分析与最终汇总，不写入 chat/conversation 相关表

CREATE TABLE IF NOT EXISTS analysis_product
(
    product_code VARCHAR(32) NOT NULL PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    create_time  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 分析任务（job 级别）
CREATE TABLE IF NOT EXISTS analysis_job
(
    job_id           VARCHAR(64)  NOT NULL PRIMARY KEY,
    product_code    VARCHAR(32)  NOT NULL,
    user_id          VARCHAR(64)  NOT NULL,
    tenant_id        VARCHAR(64)  NOT NULL DEFAULT 'default',
    input_type       VARCHAR(16)  NOT NULL DEFAULT 'TEXT', -- TEXT | TXT_FILE
    transcript_text  TEXT         NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING|RUNNING|SUCCEEDED|FAILED|PARTIAL
    error_code       VARCHAR(64),
    trace_id         VARCHAR(64),
    create_time      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    finish_time      TIMESTAMPTZ,
    CONSTRAINT fk_analysis_job_product
        FOREIGN KEY (product_code) REFERENCES analysis_product (product_code)
);

CREATE INDEX IF NOT EXISTS idx_analysis_job_user_status
    ON analysis_job (user_id, status, create_time DESC);

-- 汇总结果（JSONB）
CREATE TABLE IF NOT EXISTS analysis_job_result
(
    job_id            VARCHAR(64) NOT NULL PRIMARY KEY,
    customer_tags    JSONB,
    sales_tags       JSONB,
    summary          JSONB,
    intent           JSONB,
    aggregate        JSONB,
    raw_node_outputs JSONB,
    update_time      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_analysis_job_result_job
        FOREIGN KEY (job_id) REFERENCES analysis_job (job_id)
);

-- vta 预置产品行
INSERT INTO analysis_product (product_code, name, status)
SELECT 'VTA', '录音分析助手', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM analysis_product WHERE product_code = 'VTA');

