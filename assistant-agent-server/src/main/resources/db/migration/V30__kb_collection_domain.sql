-- V30: kb_collection 增加领域，供 knowledge_search 选择子 Agent

ALTER TABLE kb_collection ADD COLUMN IF NOT EXISTS domain_name VARCHAR(64);
ALTER TABLE kb_collection ADD COLUMN IF NOT EXISTS domain_description TEXT;
ALTER TABLE kb_collection ADD COLUMN IF NOT EXISTS llm_config_id VARCHAR(64);

COMMENT ON COLUMN kb_collection.domain_name IS '领域名。同一领域多行共用；空则不出现在 knowledge_search';
COMMENT ON COLUMN kb_collection.domain_description IS '领域短说明，写入 knowledge_search 工具说明';
COMMENT ON COLUMN kb_collection.llm_config_id IS '该领域子 Agent 的 CHAT 配置键，对齐 llm_config.config_id';
