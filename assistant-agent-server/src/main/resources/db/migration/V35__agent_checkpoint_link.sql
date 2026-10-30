-- 父 Agent 的 knowledge_search 与领域子 Agent / RAG 摘要的链接。
-- 子图仍写 GraphThread / GraphCheckpoint，线程名为 kb:{uuid}。
-- 手工脚本：schema/41_agent_checkpoint_link.sql

CREATE TABLE IF NOT EXISTS agent_checkpoint_link (
    link_id              UUID PRIMARY KEY,
    parent_thread_name   VARCHAR(255) NOT NULL,
    parent_tool_call_id  VARCHAR(128) NOT NULL,
    child_thread_name    VARCHAR(64)  NOT NULL,
    kind                 VARCHAR(16)  NOT NULL,
    domain_name          VARCHAR(128),
    query_text           TEXT,
    rewritten_queries    TEXT,
    hit_count            INT,
    error_message        VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_checkpoint_link_parent
    ON agent_checkpoint_link (parent_thread_name, parent_tool_call_id);

CREATE INDEX IF NOT EXISTS idx_agent_checkpoint_link_child
    ON agent_checkpoint_link (child_thread_name);

CREATE INDEX IF NOT EXISTS idx_agent_checkpoint_link_kind_created
    ON agent_checkpoint_link (kind, created_at DESC);
