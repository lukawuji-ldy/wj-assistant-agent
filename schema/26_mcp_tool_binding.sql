-- MCP Server 连接权威（P5.2）+ 工具绑定（P5.1 bound/enabled）
CREATE TABLE IF NOT EXISTS mcp_server_ref
(
    id                 BIGINT       PRIMARY KEY,
    server_code        VARCHAR(64)  NOT NULL,
    display_name       VARCHAR(128) NOT NULL,
    base_url           VARCHAR(512) NOT NULL,
    sse_endpoint       VARCHAR(128),
    auth_type          VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    auth_token_cipher  TEXT,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    sort_order         INT          NOT NULL DEFAULT 0,
    create_time        TIMESTAMPTZ  NOT NULL,
    update_time        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_mcp_server_code UNIQUE (server_code)
);

COMMENT ON TABLE mcp_server_ref IS 'MCP Server 连接权威（Admin CRUD）；仅 ACTIVE 建 Transport；yml server-url 仅零 ACTIVE 行兜底';
COMMENT ON COLUMN mcp_server_ref.server_code IS '业务键，全局唯一，如 wuji-mcp';
COMMENT ON COLUMN mcp_server_ref.display_name IS '展示名';
COMMENT ON COLUMN mcp_server_ref.base_url IS 'MCP Server base URL，如 http://127.0.0.1:8081';
COMMENT ON COLUMN mcp_server_ref.sse_endpoint IS 'SSE 端点，空则用全局默认 /sse';
COMMENT ON COLUMN mcp_server_ref.auth_type IS 'NONE|BEARER';
COMMENT ON COLUMN mcp_server_ref.auth_token_cipher IS 'Bearer 密文 enc:v1:；auth_type=BEARER 时使用';
COMMENT ON COLUMN mcp_server_ref.status IS 'ACTIVE|DISABLED；仅 ACTIVE 建 Client Transport';
COMMENT ON COLUMN mcp_server_ref.sort_order IS '列表排序，越小越靠前';

-- Agent 侧 MCP 工具子集绑定（行存在 = bound；enabled 仅对已绑定行有意义；解绑 = DELETE）
CREATE TABLE IF NOT EXISTS mcp_tool_binding
(
    id            BIGINT       PRIMARY KEY,
    server_code   VARCHAR(64)  NOT NULL,
    tool_name     VARCHAR(128) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    update_time   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_mcp_tool_binding UNIQUE (server_code, tool_name)
);

CREATE INDEX IF NOT EXISTS idx_mcp_tool_binding_server
    ON mcp_tool_binding (server_code, enabled);

COMMENT ON TABLE mcp_tool_binding IS 'MCP 工具绑定；行存在=已绑定；同 server 有行时仅 enabled=true 注入；有 ACTIVE server 时零绑定=该 server 全量；解绑=DELETE';
COMMENT ON COLUMN mcp_tool_binding.server_code IS '关联 mcp_server_ref.server_code';
COMMENT ON COLUMN mcp_tool_binding.tool_name IS '远端工具名';
COMMENT ON COLUMN mcp_tool_binding.enabled IS '是否注入 ReactAgent；仅对已绑定行有意义；新建绑定默认 false';
