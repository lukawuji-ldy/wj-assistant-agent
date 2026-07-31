-- Flyway V1：与 schema/ 分文件内容一致（UTF-8）
-- 对齐 docs/database-design.md

-- >>> 00_extensions.sql
-- pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- >>> 01_sys_user.sql
-- 系统用户（预置账号，无自助注册）
CREATE TABLE IF NOT EXISTS sys_user
(
    id            BIGINT       PRIMARY KEY,
    user_id       VARCHAR(64)  NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    nickname      VARCHAR(100) NOT NULL,
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    role          VARCHAR(64)  NOT NULL DEFAULT 'user',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    create_time   TIMESTAMPTZ  NOT NULL,
    update_time   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_sys_user_id UNIQUE (user_id),
    CONSTRAINT uk_sys_username UNIQUE (username)
);

COMMENT ON TABLE sys_user IS '系统预置用户表，登录鉴权用';
COMMENT ON COLUMN sys_user.id IS '主键';
COMMENT ON COLUMN sys_user.user_id IS '业务用户键，写入 JWT';
COMMENT ON COLUMN sys_user.username IS '登录用户名';
COMMENT ON COLUMN sys_user.password_hash IS '密码哈希（BCrypt 等）';
COMMENT ON COLUMN sys_user.nickname IS '展示昵称';
COMMENT ON COLUMN sys_user.tenant_id IS '租户标识，单租户可用 default';
COMMENT ON COLUMN sys_user.role IS '角色，与 RAG ACL 对齐';
COMMENT ON COLUMN sys_user.status IS '状态：ACTIVE/DISABLED 等';
COMMENT ON COLUMN sys_user.create_time IS '创建时间';
COMMENT ON COLUMN sys_user.update_time IS '更新时间';

-- >>> 02_conversation.sql
-- 会话元数据
CREATE TABLE IF NOT EXISTS conversation
(
    id                       BIGINT       PRIMARY KEY,
    conversation_id          VARCHAR(64)  NOT NULL,
    user_id                  VARCHAR(64)  NOT NULL,
    title                    VARCHAR(200),
    summary                  TEXT,
    summary_until_time       TIMESTAMPTZ,
    summary_until_message_id VARCHAR(64),
    summary_compressed_at    TIMESTAMPTZ,
    message_count            INT          NOT NULL DEFAULT 0,
    last_active_time         TIMESTAMPTZ  NOT NULL,
    create_time              TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_conversation_id UNIQUE (conversation_id)
);

CREATE INDEX IF NOT EXISTS idx_conversation_user_active
    ON conversation (user_id, last_active_time);

COMMENT ON TABLE conversation IS '聊天会话元数据';
COMMENT ON COLUMN conversation.id IS '主键';
COMMENT ON COLUMN conversation.conversation_id IS '对外业务键';
COMMENT ON COLUMN conversation.user_id IS '所属用户';
COMMENT ON COLUMN conversation.title IS '侧栏标题，可由 LLM 异步生成';
COMMENT ON COLUMN conversation.summary IS '当前生效的结构化摘要 JSON（滚动合并覆盖写）';
COMMENT ON COLUMN conversation.summary_until_time IS '摘要覆盖到的最后一条消息时间（watermark）';
COMMENT ON COLUMN conversation.summary_until_message_id IS '摘要覆盖到的最后一条消息业务键';
COMMENT ON COLUMN conversation.summary_compressed_at IS '最近一次压缩发生时间（审计）';
COMMENT ON COLUMN conversation.message_count IS '消息条数';
COMMENT ON COLUMN conversation.last_active_time IS '最后活跃时间';
COMMENT ON COLUMN conversation.create_time IS '创建时间';

-- >>> 03_chat_message.sql
-- 聊天消息（含流式状态列 STREAMING|COMPLETED|CANCELLED）
CREATE TABLE IF NOT EXISTS chat_message
(
    id              BIGINT       PRIMARY KEY,
    message_id      VARCHAR(64)  NOT NULL,
    conversation_id VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(64)  NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    content         TEXT         NOT NULL,
    token_count     INT          NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED',
    create_time     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_message_id UNIQUE (message_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_message_conv_time
    ON chat_message (conversation_id, create_time);

COMMENT ON TABLE chat_message IS '会话消息（短期记忆）';
COMMENT ON COLUMN chat_message.id IS '主键';
COMMENT ON COLUMN chat_message.message_id IS '消息业务键';
COMMENT ON COLUMN chat_message.conversation_id IS '所属会话业务键';
COMMENT ON COLUMN chat_message.user_id IS '所属用户';
COMMENT ON COLUMN chat_message.role IS '角色：user|assistant|system|tool';
COMMENT ON COLUMN chat_message.content IS '消息正文';
COMMENT ON COLUMN chat_message.token_count IS '估算 token 数';
COMMENT ON COLUMN chat_message.status IS '流式状态：STREAMING|COMPLETED|CANCELLED';
COMMENT ON COLUMN chat_message.create_time IS '创建时间';

-- >>> 04_user_profile.sql
-- 用户结构化画像 / 偏好
CREATE TABLE IF NOT EXISTS user_profile
(
    id             BIGINT         PRIMARY KEY,
    memory_id      VARCHAR(64)    NOT NULL,
    user_id        VARCHAR(64)    NOT NULL,
    memory_type    VARCHAR(32)    NOT NULL,
    memory_key     VARCHAR(128)   NOT NULL,
    memory_value   TEXT           NOT NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    confidence     NUMERIC(3, 2)  NOT NULL DEFAULT 1.00,
    importance     NUMERIC(3, 2)  NOT NULL DEFAULT 0.50,
    source         VARCHAR(32)    NOT NULL,
    version        INT            NOT NULL DEFAULT 1,
    expire_time    TIMESTAMPTZ,
    last_used_time TIMESTAMPTZ,
    create_time    TIMESTAMPTZ    NOT NULL,
    update_time    TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uk_user_profile_memory_id UNIQUE (memory_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_profile_active_key
    ON user_profile (user_id, memory_key)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_user_profile_user_status
    ON user_profile (user_id, status);

CREATE INDEX IF NOT EXISTS idx_user_profile_expire
    ON user_profile (status, expire_time);

COMMENT ON TABLE user_profile IS '用户结构化长期记忆（画像/偏好）';
COMMENT ON COLUMN user_profile.id IS '主键';
COMMENT ON COLUMN user_profile.memory_id IS '记忆业务键';
COMMENT ON COLUMN user_profile.user_id IS '所属用户';
COMMENT ON COLUMN user_profile.memory_type IS '类型：PROFILE|PREFERENCE';
COMMENT ON COLUMN user_profile.memory_key IS '稳定键，如 occupation、goal.current';
COMMENT ON COLUMN user_profile.memory_value IS '记忆值，须自包含语境';
COMMENT ON COLUMN user_profile.status IS '状态：ACTIVE|INACTIVE|DELETED|EXPIRED';
COMMENT ON COLUMN user_profile.confidence IS '置信度 0~1';
COMMENT ON COLUMN user_profile.importance IS '重要度 0~1';
COMMENT ON COLUMN user_profile.source IS '来源：USER_DIRECT|EXTRACTED|MERGED|SYSTEM';
COMMENT ON COLUMN user_profile.version IS '同 key 演进版本号';
COMMENT ON COLUMN user_profile.expire_time IS '过期时间，空表示永久';
COMMENT ON COLUMN user_profile.last_used_time IS '最近检索命中时间';
COMMENT ON COLUMN user_profile.create_time IS '创建时间';
COMMENT ON COLUMN user_profile.update_time IS '更新时间';

-- >>> 05_memory_extract_log.sql
-- 记忆提取审计
CREATE TABLE IF NOT EXISTS memory_extract_log
(
    id              BIGINT       PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL,
    conversation_id VARCHAR(64)  NOT NULL,
    message_id      VARCHAR(64)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    action          VARCHAR(20),
    result_type     VARCHAR(32),
    error_message   VARCHAR(512),
    retry_count     INT          NOT NULL DEFAULT 0,
    create_time     TIMESTAMPTZ  NOT NULL,
    update_time     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_message_extract UNIQUE (message_id)
);

CREATE INDEX IF NOT EXISTS idx_memory_extract_retry
    ON memory_extract_log (status, retry_count, update_time);

COMMENT ON TABLE memory_extract_log IS '长期记忆提取幂等与审计日志';
COMMENT ON COLUMN memory_extract_log.id IS '主键';
COMMENT ON COLUMN memory_extract_log.user_id IS '用户';
COMMENT ON COLUMN memory_extract_log.conversation_id IS '会话';
COMMENT ON COLUMN memory_extract_log.message_id IS '触发提取的消息业务键';
COMMENT ON COLUMN memory_extract_log.status IS 'PENDING|SUCCESS|FAILED|SKIPPED';
COMMENT ON COLUMN memory_extract_log.action IS 'INSERT|UPDATE|MERGE|DELETE|IGNORE';
COMMENT ON COLUMN memory_extract_log.result_type IS 'PROFILE|PREFERENCE|SEMANTIC|NONE';
COMMENT ON COLUMN memory_extract_log.error_message IS '失败原因摘要';
COMMENT ON COLUMN memory_extract_log.retry_count IS '重试次数';
COMMENT ON COLUMN memory_extract_log.create_time IS '创建时间';
COMMENT ON COLUMN memory_extract_log.update_time IS '更新时间';

-- >>> 06_llm_config.sql
-- LLM 连接配置
CREATE TABLE IF NOT EXISTS llm_config
(
    id              BIGINT        PRIMARY KEY,
    config_id       VARCHAR(64)   NOT NULL,
    name            VARCHAR(128)  NOT NULL,
    provider        VARCHAR(64)   NOT NULL DEFAULT 'openai_compatible',
    base_url        VARCHAR(512)  NOT NULL,
    api_key_cipher  TEXT          NOT NULL,
    model           VARCHAR(128)  NOT NULL,
    temperature     NUMERIC(4, 2),
    max_tokens      INT,
    extra_json      JSONB,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    create_time     TIMESTAMPTZ   NOT NULL,
    update_time     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_llm_config_id UNIQUE (config_id)
);

CREATE INDEX IF NOT EXISTS idx_llm_config_status ON llm_config (status);

COMMENT ON TABLE llm_config IS '大模型 OpenAI Compatible 连接配置';
COMMENT ON COLUMN llm_config.id IS '主键';
COMMENT ON COLUMN llm_config.config_id IS '配置业务键，如 llm_primary';
COMMENT ON COLUMN llm_config.name IS '展示名称';
COMMENT ON COLUMN llm_config.provider IS '提供商标识，默认 openai_compatible';
COMMENT ON COLUMN llm_config.base_url IS 'API Base URL';
COMMENT ON COLUMN llm_config.api_key_cipher IS 'API Key 密文';
COMMENT ON COLUMN llm_config.model IS '模型名';
COMMENT ON COLUMN llm_config.temperature IS '温度';
COMMENT ON COLUMN llm_config.max_tokens IS '最大生成 token';
COMMENT ON COLUMN llm_config.extra_json IS '扩展参数 JSON';
COMMENT ON COLUMN llm_config.status IS 'ACTIVE/DISABLED';
COMMENT ON COLUMN llm_config.create_time IS '创建时间';
COMMENT ON COLUMN llm_config.update_time IS '更新时间';

-- >>> 07_prompt_template.sql
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

-- >>> 08_llm_call_log.sql
-- 入模审计
CREATE TABLE IF NOT EXISTS llm_call_log
(
    id                BIGINT       PRIMARY KEY,
    call_id           VARCHAR(64)  NOT NULL,
    trace_id          VARCHAR(64),
    conversation_id   VARCHAR(64),
    message_id        VARCHAR(64),
    user_id           VARCHAR(64),
    model_id          VARCHAR(128) NOT NULL,
    provider          VARCHAR(64),
    attempt           INT          NOT NULL DEFAULT 1,
    is_fallback       BOOLEAN      NOT NULL DEFAULT FALSE,
    status            VARCHAR(20)  NOT NULL,
    error_code        VARCHAR(64),
    latency_ms        INT,
    prompt_tokens     INT,
    completion_tokens INT,
    request_json      JSONB        NOT NULL,
    response_json     JSONB,
    create_time       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_llm_call_id UNIQUE (call_id)
);

CREATE INDEX IF NOT EXISTS idx_llm_call_conv ON llm_call_log (conversation_id, create_time);
CREATE INDEX IF NOT EXISTS idx_llm_call_user ON llm_call_log (user_id, create_time);
CREATE INDEX IF NOT EXISTS idx_llm_call_trace ON llm_call_log (trace_id);

COMMENT ON TABLE llm_call_log IS '每次 LLM 调用的完整入模参数审计（评测预留）';
COMMENT ON COLUMN llm_call_log.id IS '主键';
COMMENT ON COLUMN llm_call_log.call_id IS '单次调用业务键';
COMMENT ON COLUMN llm_call_log.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN llm_call_log.conversation_id IS '会话 ID';
COMMENT ON COLUMN llm_call_log.message_id IS '关联消息 ID';
COMMENT ON COLUMN llm_call_log.user_id IS '用户 ID';
COMMENT ON COLUMN llm_call_log.model_id IS '实际使用的模型/config';
COMMENT ON COLUMN llm_call_log.provider IS '提供商';
COMMENT ON COLUMN llm_call_log.attempt IS '第几次尝试';
COMMENT ON COLUMN llm_call_log.is_fallback IS '是否备用模型';
COMMENT ON COLUMN llm_call_log.status IS 'SUCCESS/FAILED 等';
COMMENT ON COLUMN llm_call_log.error_code IS '错误码';
COMMENT ON COLUMN llm_call_log.latency_ms IS '耗时毫秒';
COMMENT ON COLUMN llm_call_log.prompt_tokens IS '输入 token';
COMMENT ON COLUMN llm_call_log.completion_tokens IS '输出 token';
COMMENT ON COLUMN llm_call_log.request_json IS '完整请求（messages/tools/options）';
COMMENT ON COLUMN llm_call_log.response_json IS '完整响应或流式拼接结果';
COMMENT ON COLUMN llm_call_log.create_time IS '创建时间';

-- >>> 09_kb_document.sql
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

-- >>> 10_kb_document_version.sql
-- 知识库文档版本
CREATE TABLE IF NOT EXISTS kb_document_version
(
    id            BIGINT       PRIMARY KEY,
    doc_id        VARCHAR(64)  NOT NULL,
    version       VARCHAR(32)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    source        VARCHAR(500),
    acl_roles     JSONB        NOT NULL,
    published_at  TIMESTAMPTZ,
    deprecated_at TIMESTAMPTZ,
    create_time   TIMESTAMPTZ  NOT NULL,
    update_time   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_kb_doc_version UNIQUE (doc_id, version)
);

CREATE INDEX IF NOT EXISTS idx_kb_doc_version_status
    ON kb_document_version (doc_id, status);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_doc_one_active
    ON kb_document_version (doc_id)
    WHERE status = 'ACTIVE';

COMMENT ON TABLE kb_document_version IS '知识库文档版本（停用≠删除）';
COMMENT ON COLUMN kb_document_version.id IS '主键';
COMMENT ON COLUMN kb_document_version.doc_id IS '逻辑文档业务键';
COMMENT ON COLUMN kb_document_version.version IS '版本号，如 v3';
COMMENT ON COLUMN kb_document_version.status IS 'DRAFT|ACTIVE|DEPRECATED';
COMMENT ON COLUMN kb_document_version.source IS '来源文件或 URI';
COMMENT ON COLUMN kb_document_version.acl_roles IS '可见角色列表 JSON';
COMMENT ON COLUMN kb_document_version.published_at IS '发布时间';
COMMENT ON COLUMN kb_document_version.deprecated_at IS '停用时间';
COMMENT ON COLUMN kb_document_version.create_time IS '创建时间';
COMMENT ON COLUMN kb_document_version.update_time IS '更新时间';

-- >>> 11_kb_citation_snapshot.sql
-- 知识引用不可变快照
CREATE TABLE IF NOT EXISTS kb_citation_snapshot
(
    id               BIGINT        PRIMARY KEY,
    message_id       VARCHAR(64),
    ticket_id        VARCHAR(64),
    user_id          VARCHAR(64)   NOT NULL,
    doc_id           VARCHAR(64)   NOT NULL,
    version_id       BIGINT        NOT NULL,
    version          VARCHAR(32)   NOT NULL,
    section          VARCHAR(500),
    chunk_id         VARCHAR(128)  NOT NULL,
    score            NUMERIC(6, 4),
    content_snapshot TEXT          NOT NULL,
    content_hash     VARCHAR(64)   NOT NULL,
    cited_at         TIMESTAMPTZ   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_citation_message ON kb_citation_snapshot (message_id);
CREATE INDEX IF NOT EXISTS idx_citation_ticket ON kb_citation_snapshot (ticket_id);
CREATE INDEX IF NOT EXISTS idx_citation_doc_version ON kb_citation_snapshot (doc_id, version_id);

COMMENT ON TABLE kb_citation_snapshot IS '知识引用不可变快照（历史不可被当前 ACTIVE 覆盖）';
COMMENT ON COLUMN kb_citation_snapshot.id IS '主键';
COMMENT ON COLUMN kb_citation_snapshot.message_id IS '关联消息';
COMMENT ON COLUMN kb_citation_snapshot.ticket_id IS '关联工单（可选）';
COMMENT ON COLUMN kb_citation_snapshot.user_id IS '用户';
COMMENT ON COLUMN kb_citation_snapshot.doc_id IS '文档业务键';
COMMENT ON COLUMN kb_citation_snapshot.version_id IS '版本主键';
COMMENT ON COLUMN kb_citation_snapshot.version IS '版本号';
COMMENT ON COLUMN kb_citation_snapshot.section IS '章节路径';
COMMENT ON COLUMN kb_citation_snapshot.chunk_id IS '片段 ID';
COMMENT ON COLUMN kb_citation_snapshot.score IS '当时命中得分';
COMMENT ON COLUMN kb_citation_snapshot.content_snapshot IS '当时片段正文快照';
COMMENT ON COLUMN kb_citation_snapshot.content_hash IS '正文哈希';
COMMENT ON COLUMN kb_citation_snapshot.cited_at IS '引用时间';

-- >>> 12_vector_store.sql
-- 企业知识库向量（Spring AI PGVector 兼容，维度默认 1536）
CREATE TABLE IF NOT EXISTS vector_store
(
    id        UUID PRIMARY KEY,
    content   TEXT,
    metadata  JSONB,
    embedding VECTOR(1536)
);

DO $$
BEGIN
    BEGIN
        CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
            ON vector_store USING ivfflat (embedding vector_cosine_ops)
            WITH (lists = 100);
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'skip idx_vector_store_embedding: %', SQLERRM;
    END;
END $$;

CREATE INDEX IF NOT EXISTS idx_vector_store_meta_doc
    ON vector_store ((metadata ->> 'doc_id'), (metadata ->> 'status'));

COMMENT ON TABLE vector_store IS '企业知识库向量切片表';
COMMENT ON COLUMN vector_store.id IS '切片主键 UUID';
COMMENT ON COLUMN vector_store.content IS '片段正文';
COMMENT ON COLUMN vector_store.metadata IS '元数据 JSON：doc_id/version/section/chunk_id 等';
COMMENT ON COLUMN vector_store.embedding IS '向量，维度须与 Embedding 模型一致';

-- >>> 13_user_semantic_memory.sql
-- 用户语义长期记忆（与知识库分表）
CREATE TABLE IF NOT EXISTS user_semantic_memory
(
    id                UUID PRIMARY KEY,
    user_id           VARCHAR(64)  NOT NULL,
    content           TEXT         NOT NULL,
    memory_type       VARCHAR(32)  NOT NULL DEFAULT 'experience',
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    importance        REAL         NOT NULL DEFAULT 0.5,
    confidence        REAL         NOT NULL DEFAULT 0.8,
    tags              JSONB,
    metadata          JSONB,
    source            VARCHAR(32)  NOT NULL DEFAULT 'EXTRACTED',
    source_message_id VARCHAR(64),
    expire_time       TIMESTAMPTZ,
    last_used_time    TIMESTAMPTZ,
    embedding         VECTOR(1536) NOT NULL,
    create_time       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_semantic_user_status
    ON user_semantic_memory (user_id, status);

CREATE INDEX IF NOT EXISTS idx_user_semantic_expire
    ON user_semantic_memory (status, expire_time);

DO $$
BEGIN
    BEGIN
        CREATE INDEX IF NOT EXISTS idx_user_semantic_embedding
            ON user_semantic_memory USING ivfflat (embedding vector_cosine_ops)
            WITH (lists = 100);
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'skip idx_user_semantic_embedding: %', SQLERRM;
    END;
END $$;

COMMENT ON TABLE user_semantic_memory IS '用户语义长期记忆向量表（非企业知识库）';
COMMENT ON COLUMN user_semantic_memory.id IS '主键 UUID';
COMMENT ON COLUMN user_semantic_memory.user_id IS '所属用户，检索必须过滤';
COMMENT ON COLUMN user_semantic_memory.content IS '叙述性记忆正文';
COMMENT ON COLUMN user_semantic_memory.memory_type IS 'experience|project|note 等';
COMMENT ON COLUMN user_semantic_memory.status IS 'ACTIVE 等生命周期状态';
COMMENT ON COLUMN user_semantic_memory.importance IS '重要度';
COMMENT ON COLUMN user_semantic_memory.confidence IS '置信度';
COMMENT ON COLUMN user_semantic_memory.tags IS '标签 JSON 数组';
COMMENT ON COLUMN user_semantic_memory.metadata IS '扩展元数据';
COMMENT ON COLUMN user_semantic_memory.source IS 'EXTRACTED 等';
COMMENT ON COLUMN user_semantic_memory.source_message_id IS '溯源消息 ID';
COMMENT ON COLUMN user_semantic_memory.expire_time IS '过期时间';
COMMENT ON COLUMN user_semantic_memory.last_used_time IS '最近使用时间';
COMMENT ON COLUMN user_semantic_memory.embedding IS '向量';
COMMENT ON COLUMN user_semantic_memory.create_time IS '创建时间';
COMMENT ON COLUMN user_semantic_memory.update_time IS '更新时间';

