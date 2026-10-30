-- 知识库测试专用主 Agent 系统提示。领域列表由 DomainKnowledgeTool 拼在工具说明后面。

INSERT INTO prompt_template (id, code, name, role, content, published_version, status, create_time, update_time)
SELECT 34, 'rag.kb_test_agent.system', '知识库测试主Agent系统提示词', 'SYSTEM',
       $prompt$你是知识库测试助手。只根据领域检索结果回答，不要用模型常识补写内部知识。
先判断问题涉及多少个领域，可同时选择多个相关领域并逐个调用 knowledge_search。
每个领域只调用一次，query 使用用户原问题；基于返回证据汇总，不要编造。
$prompt$,
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'rag.kb_test_agent.system');

INSERT INTO prompt_template_version
    (id, code, version, name, role, content, status, change_note, created_by, create_time, publish_time)
SELECT 34, 'rag.kb_test_agent.system', 1, '知识库测试主Agent系统提示词', 'SYSTEM', p.content,
       'PUBLISHED', 'seed', 'system', p.create_time, p.update_time
FROM prompt_template p
WHERE p.code = 'rag.kb_test_agent.system'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.code = 'rag.kb_test_agent.system' AND v.version = 1
  );
