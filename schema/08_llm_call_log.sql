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
