-- 38 领域子 Agent 系统提示（对齐 Flyway V32）

INSERT INTO prompt_template (id, code, name, role, content, published_version, status, create_time, update_time)
SELECT 33, 'rag.domain_agent.system', '知识库领域子Agent系统提示词', 'SYSTEM',
       $prompt$只根据本领域检索结果回答，不要编造。需要证据时调用 search_collection。
$prompt$,
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'rag.domain_agent.system');

INSERT INTO prompt_template_version
    (id, code, version, name, role, content, status, change_note, created_by, create_time, publish_time)
SELECT 33, 'rag.domain_agent.system', 1, '知识库领域子Agent系统提示词', 'SYSTEM', p.content,
       'PUBLISHED', 'seed', 'system', p.create_time, p.update_time
FROM prompt_template p
WHERE p.code = 'rag.domain_agent.system'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.code = 'rag.domain_agent.system' AND v.version = 1
  );
