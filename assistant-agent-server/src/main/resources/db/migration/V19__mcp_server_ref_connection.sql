-- P5.2：mcp_server_ref 升为连接权威（URL / SSE / Bearer 密文）
ALTER TABLE mcp_server_ref ADD COLUMN IF NOT EXISTS base_url VARCHAR(512);
ALTER TABLE mcp_server_ref ADD COLUMN IF NOT EXISTS sse_endpoint VARCHAR(128);
ALTER TABLE mcp_server_ref ADD COLUMN IF NOT EXISTS auth_type VARCHAR(16) NOT NULL DEFAULT 'NONE';
ALTER TABLE mcp_server_ref ADD COLUMN IF NOT EXISTS auth_token_cipher TEXT;
ALTER TABLE mcp_server_ref ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;

UPDATE mcp_server_ref
SET base_url = 'http://127.0.0.1:8081'
WHERE base_url IS NULL OR btrim(base_url) = '';

ALTER TABLE mcp_server_ref ALTER COLUMN base_url SET NOT NULL;

COMMENT ON TABLE mcp_server_ref IS 'MCP Server 连接权威（Admin CRUD）；仅 ACTIVE 建 Transport；yml server-url 仅零 ACTIVE 行兜底';
COMMENT ON COLUMN mcp_server_ref.server_code IS '业务键，全局唯一，如 wuji-mcp';
COMMENT ON COLUMN mcp_server_ref.display_name IS '展示名';
COMMENT ON COLUMN mcp_server_ref.base_url IS 'MCP Server base URL，如 http://127.0.0.1:8081';
COMMENT ON COLUMN mcp_server_ref.sse_endpoint IS 'SSE 端点，空则用全局默认 /sse';
COMMENT ON COLUMN mcp_server_ref.auth_type IS 'NONE|BEARER';
COMMENT ON COLUMN mcp_server_ref.auth_token_cipher IS 'Bearer 密文 enc:v1:；auth_type=BEARER 时使用';
COMMENT ON COLUMN mcp_server_ref.status IS 'ACTIVE|DISABLED；仅 ACTIVE 建 Client Transport';
COMMENT ON COLUMN mcp_server_ref.sort_order IS '列表排序，越小越靠前';
