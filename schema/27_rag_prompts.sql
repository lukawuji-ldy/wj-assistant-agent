-- 知识库提示词种子（空库 / 手工 schema）；与 Flyway V21 对齐

INSERT INTO prompt_template (id, code, name, role, content, published_version, status, create_time, update_time)
SELECT 20, 'rag.knowledge_retrieval.system', '知识库检索工具说明', 'SYSTEM',
       $prompt$
按领域检索知识库并返回结论。制度/FAQ/人物经历/产品说明等凡可能被文档回答的问题都必须调用 knowledge_search，传入 query 与 domain。domain 只能从下列领域中选择，不要传入 userId 或 collection。
$prompt$,
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'rag.knowledge_retrieval.system');

INSERT INTO prompt_template_version
    (id, code, version, name, role, content, status, change_note, created_by, create_time, publish_time)
SELECT 20, 'rag.knowledge_retrieval.system', 1, '知识库检索工具说明', 'SYSTEM', p.content,
       'PUBLISHED', 'seed', 'system', p.create_time, p.update_time
FROM prompt_template p
WHERE p.code = 'rag.knowledge_retrieval.system'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.code = 'rag.knowledge_retrieval.system' AND v.version = 1
  );

INSERT INTO prompt_template (id, code, name, role, content, published_version, status, create_time, update_time)
SELECT 21, 'rag.answer.system', '知识库回答系统提示词', 'SYSTEM',
       $prompt$
凡用户问题可能被已入库文档回答（制度、FAQ、人物经历、产品说明等），必须先调用 knowledge_search(query, domain)。
仅当没有可用领域，或工具返回无法检索、无可靠命中时，才能说「不在知识库范围内」；禁止仅凭「企业助手」身份否定库中已有内容。
回答须依据工具返回的结论，禁止编造。
$prompt$,
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'rag.answer.system');

INSERT INTO prompt_template_version
    (id, code, version, name, role, content, status, change_note, created_by, create_time, publish_time)
SELECT 21, 'rag.answer.system', 1, '知识库回答系统提示词', 'SYSTEM', p.content,
       'PUBLISHED', 'seed', 'system', p.create_time, p.update_time
FROM prompt_template p
WHERE p.code = 'rag.answer.system'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.code = 'rag.answer.system' AND v.version = 1
  );
