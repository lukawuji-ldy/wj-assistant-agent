-- 记忆抽取 Prompt 种子（空库 / 手工 schema 落库）
-- code 按是否存在插入，不短路其它 prompt；同时写入版本表 PUBLISHED v1

INSERT INTO prompt_template (id, code, name, role, content, published_version, status, create_time, update_time)
SELECT 10, 'memory.extract.system', '记忆抽取系统提示词', 'SYSTEM',
       $prompt$
你是企业助手的长期记忆抽取器。根据「用户原文」与「助手回复」产出可落库的 Memory Action 列表。
只输出 JSON（不要 markdown 解释），schema：
{"actions":[{"action":"INSERT|UPDATE|MERGE|DELETE|IGNORE","type":"PROFILE|PREFERENCE|SEMANTIC|NONE","key":"...","newValue":"...","content":"...","confidence":0.0,"importance":0.0,"reason":"..."}]}

分流（强制）：
1) 有稳定 memory_key 且值可独立理解、可覆盖演进 → type=PROFILE 或 PREFERENCE（写 user_profile）
2) 有长期价值但只能靠语义理解/召回 → type=SEMANTIC，必须填 content（写 user_semantic_memory）
3) 无长期价值（闲聊、一次性）→ IGNORE

键值约定示例：display_name（仅「我叫」）、hometown、residence、self_desc、occupation、goal.current、preference.*；禁止用「我是」写 display_name。
同一句话可拆多条 Action；禁止把整段对话原文入库；禁止写入企业知识库内容。

约束（强制）：
- 条件/未来/学习中（希望、以后、打算、正在学）不得写成无修饰的绝对事实 key（如裸 tech.language）
- newValue / content 须离开原句仍可理解（自包含）；宁可写完整短语，禁止裸值如单独「Java」充当主语言
- 能用 goal.current 表达完整意图时不要再拆绝对 tech.language
- 弱模态显著降低 confidence，或只写 SEMANTIC
- 无把握时 IGNORE
$prompt$,
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'memory.extract.system');

INSERT INTO prompt_template_version
    (id, code, version, name, role, content, status, change_note, created_by, create_time, publish_time)
SELECT 10, 'memory.extract.system', 1, '记忆抽取系统提示词', 'SYSTEM', p.content,
       'PUBLISHED', 'seed', 'system', p.create_time, p.update_time
FROM prompt_template p
WHERE p.code = 'memory.extract.system'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.code = 'memory.extract.system' AND v.version = 1
  );

INSERT INTO prompt_template (id, code, name, role, content, published_version, status, create_time, update_time)
SELECT 11, 'memory.extract.user', '记忆抽取用户提示词', 'USER',
       E'用户原文:\n{{user_text}}\n\n助手回复:\n{{assistant_text}}',
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'memory.extract.user');

INSERT INTO prompt_template_version
    (id, code, version, name, role, content, status, change_note, created_by, create_time, publish_time)
SELECT 11, 'memory.extract.user', 1, '记忆抽取用户提示词', 'USER', p.content,
       'PUBLISHED', 'seed', 'system', p.create_time, p.update_time
FROM prompt_template p
WHERE p.code = 'memory.extract.user'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.code = 'memory.extract.user' AND v.version = 1
  );
