-- 检索前查询改写与关系类回答提示（失败时用原问题）

INSERT INTO prompt_template (id, code, name, role, content, published_version, status, create_time, update_time)
SELECT 30, 'rag.query_rewrite.system', '知识库查询改写系统提示词', 'SYSTEM',
       $prompt$你是检索查询改写器。只输出 JSON，不要 markdown。
schema：{"queries":["同义或更具体的检索问句"],"relational":false}
queries 给出 2 到 3 条，覆盖同义词和上位概念（例如车厘子与樱桃），不要回答问题本身。
若问题在问两个事物的关系、对比或区别，relational 设为 true。
$prompt$,
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'rag.query_rewrite.system');

INSERT INTO prompt_template_version
    (id, code, version, name, role, content, status, change_note, created_by, create_time, publish_time)
SELECT 30, 'rag.query_rewrite.system', 1, '知识库查询改写系统提示词', 'SYSTEM', p.content,
       'PUBLISHED', 'seed', 'system', p.create_time, p.update_time
FROM prompt_template p
WHERE p.code = 'rag.query_rewrite.system'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.code = 'rag.query_rewrite.system' AND v.version = 1
  );

INSERT INTO prompt_template (id, code, name, role, content, published_version, status, create_time, update_time)
SELECT 31, 'rag.query_rewrite.user', '知识库查询改写用户提示词', 'USER',
       $prompt$原问题:
{{query}}
$prompt$,
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'rag.query_rewrite.user');

INSERT INTO prompt_template_version
    (id, code, version, name, role, content, status, change_note, created_by, create_time, publish_time)
SELECT 31, 'rag.query_rewrite.user', 1, '知识库查询改写用户提示词', 'USER', p.content,
       'PUBLISHED', 'seed', 'system', p.create_time, p.update_time
FROM prompt_template p
WHERE p.code = 'rag.query_rewrite.user'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.code = 'rag.query_rewrite.user' AND v.version = 1
  );

INSERT INTO prompt_template (id, code, name, role, content, published_version, status, create_time, update_time)
SELECT 32, 'rag.relation.system', '知识库关系类回答提示词', 'SYSTEM',
       $prompt$这是关系或对比类问题。请按「实体、关系、依据」三段回答，每段只使用已召回片段，不要补充知识库之外的事实。
$prompt$,
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'rag.relation.system');

INSERT INTO prompt_template_version
    (id, code, version, name, role, content, status, change_note, created_by, create_time, publish_time)
SELECT 32, 'rag.relation.system', 1, '知识库关系类回答提示词', 'SYSTEM', p.content,
       'PUBLISHED', 'seed', 'system', p.create_time, p.update_time
FROM prompt_template p
WHERE p.code = 'rag.relation.system'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.code = 'rag.relation.system' AND v.version = 1
  );
