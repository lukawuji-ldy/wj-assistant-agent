-- V25: 会话摘要滚动合并提示词（LLM 失败时代码降级规则摘要）

INSERT INTO prompt_template (id, code, name, role, content, published_version, status, create_time, update_time)
SELECT 28, 'memory.summary.system', '会话摘要系统提示词', 'SYSTEM',
       $prompt$你是会话摘要器。只输出 JSON，不要 markdown。
schema：{"goal":"","facts":[],"decisions":[],"pending_tasks":[]}
将旧摘要与待压缩消息滚动合并。保留目标、关键事实、已做决策、未完成任务；删除闲聊。
$prompt$,
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'memory.summary.system');

INSERT INTO prompt_template_version
    (id, code, version, name, role, content, status, change_note, created_by, create_time, publish_time)
SELECT 28, 'memory.summary.system', 1, '会话摘要系统提示词', 'SYSTEM', p.content,
       'PUBLISHED', 'seed', 'system', p.create_time, p.update_time
FROM prompt_template p
WHERE p.code = 'memory.summary.system'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.code = 'memory.summary.system' AND v.version = 1
  );

INSERT INTO prompt_template (id, code, name, role, content, published_version, status, create_time, update_time)
SELECT 29, 'memory.summary.user', '会话摘要用户提示词', 'USER',
       $prompt$旧摘要:
{{old_summary}}

待压缩消息:
{{messages}}
$prompt$,
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'memory.summary.user');

INSERT INTO prompt_template_version
    (id, code, version, name, role, content, status, change_note, created_by, create_time, publish_time)
SELECT 29, 'memory.summary.user', 1, '会话摘要用户提示词', 'USER', p.content,
       'PUBLISHED', 'seed', 'system', p.create_time, p.update_time
FROM prompt_template p
WHERE p.code = 'memory.summary.user'
  AND NOT EXISTS (
      SELECT 1 FROM prompt_template_version v
      WHERE v.code = 'memory.summary.user' AND v.version = 1
  );
