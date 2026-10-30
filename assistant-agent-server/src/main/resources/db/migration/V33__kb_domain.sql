-- 领域代替集合：kb_domain 为路由权威；文档与切片增加 domain_name。
-- collection 列与 kb_collection 保留，不再参与检索。

CREATE TABLE IF NOT EXISTS kb_domain (
    domain_name    varchar(64) PRIMARY KEY,
    description    text        NOT NULL,
    llm_config_id  varchar(64) NOT NULL,
    update_time    timestamptz NOT NULL
);

INSERT INTO kb_domain (domain_name, description, llm_config_id, update_time)
SELECT DISTINCT ON (btrim(c.domain_name))
       btrim(c.domain_name),
       COALESCE(
           (SELECT btrim(c2.domain_description)
            FROM kb_collection c2
            WHERE btrim(c2.domain_name) = btrim(c.domain_name)
              AND c2.domain_description IS NOT NULL
              AND btrim(c2.domain_description) <> ''
            ORDER BY c2.collection
            LIMIT 1),
           btrim(c.domain_name)),
       btrim(c.llm_config_id),
       COALESCE(c.update_time, NOW())
FROM kb_collection c
WHERE c.domain_name IS NOT NULL
  AND btrim(c.domain_name) <> ''
  AND c.llm_config_id IS NOT NULL
  AND btrim(c.llm_config_id) <> ''
ORDER BY btrim(c.domain_name), c.collection
ON CONFLICT (domain_name) DO NOTHING;

ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS domain_name varchar(64);
ALTER TABLE kb_chunk ADD COLUMN IF NOT EXISTS domain_name varchar(64);

UPDATE kb_document d
SET domain_name = btrim(c.domain_name)
FROM kb_collection c
WHERE d.collection = c.collection
  AND c.domain_name IS NOT NULL
  AND btrim(c.domain_name) <> ''
  AND (d.domain_name IS NULL OR btrim(d.domain_name) = '');

UPDATE kb_chunk k
SET domain_name = btrim(c.domain_name)
FROM kb_collection c
WHERE k.collection = c.collection
  AND c.domain_name IS NOT NULL
  AND btrim(c.domain_name) <> ''
  AND (k.domain_name IS NULL OR btrim(k.domain_name) = '');

CREATE INDEX IF NOT EXISTS idx_kb_document_domain ON kb_document (domain_name);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_domain ON kb_chunk (domain_name);

UPDATE prompt_template
SET content = '只根据本领域检索结果回答，不要编造。需要证据时调用 search_domain。',
    update_time = NOW()
WHERE code = 'rag.domain_agent.system';

UPDATE prompt_template_version
SET content = '只根据本领域检索结果回答，不要编造。需要证据时调用 search_domain。'
WHERE code = 'rag.domain_agent.system';
