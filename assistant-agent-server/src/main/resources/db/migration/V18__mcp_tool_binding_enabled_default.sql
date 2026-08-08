-- P5.1：新建绑定默认禁用；行存在 = bound，解绑 = DELETE
ALTER TABLE mcp_tool_binding ALTER COLUMN enabled SET DEFAULT FALSE;

COMMENT ON TABLE mcp_tool_binding IS 'MCP 工具绑定；行存在=已绑定；同 server 有行时仅 enabled=true 注入，空库回落 yml includeTools；解绑=DELETE';
COMMENT ON COLUMN mcp_tool_binding.server_code IS '关联 mcp_server_ref.server_code';
COMMENT ON COLUMN mcp_tool_binding.tool_name IS '远端工具名';
COMMENT ON COLUMN mcp_tool_binding.enabled IS '是否注入 ReactAgent；仅对已绑定行有意义；新建绑定默认 false';
