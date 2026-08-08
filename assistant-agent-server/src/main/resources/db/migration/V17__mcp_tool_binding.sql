-- MCP Server 引用 + 工具绑定（Admin P5）
CREATE TABLE IF NOT EXISTS mcp_server_ref
(
    id            BIGINT       PRIMARY KEY,
    server_code   VARCHAR(64)  NOT NULL,
    display_name  VARCHAR(128) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    create_time   TIMESTAMPTZ  NOT NULL,
    update_time   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_mcp_server_code UNIQUE (server_code)
);

COMMENT ON TABLE mcp_server_ref IS 'MCP Server 引用（管理台展示）；URL 不以本表为权威';
COMMENT ON COLUMN mcp_server_ref.server_code IS '业务键，如 wuji-mcp';
COMMENT ON COLUMN mcp_server_ref.display_name IS '展示名';
COMMENT ON COLUMN mcp_server_ref.status IS 'ACTIVE|DISABLED';

CREATE TABLE IF NOT EXISTS mcp_tool_binding
(
    id            BIGINT       PRIMARY KEY,
    server_code   VARCHAR(64)  NOT NULL,
    tool_name     VARCHAR(128) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    update_time   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_mcp_tool_binding UNIQUE (server_code, tool_name)
);

CREATE INDEX IF NOT EXISTS idx_mcp_tool_binding_server
    ON mcp_tool_binding (server_code, enabled);

COMMENT ON TABLE mcp_tool_binding IS 'MCP 工具绑定；同 server 有行时优先于 yml includeTools';
COMMENT ON COLUMN mcp_tool_binding.server_code IS '关联 mcp_server_ref.server_code';
COMMENT ON COLUMN mcp_tool_binding.tool_name IS '远端工具名';
COMMENT ON COLUMN mcp_tool_binding.enabled IS '是否注入 ReactAgent';
