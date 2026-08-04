-- V8: memory retrieve router prompts（hybrid Router）
INSERT INTO prompt_template (id, code, name, role, content, version, status, create_time, update_time)
SELECT 12, 'memory.retrieve.router.system', '记忆检索路由系统提示词', 'SYSTEM',
       $prompt$
你是企业助手的长期记忆路由判定器。根据用户本轮问句，判断是否需要加载该用户的长期记忆。
只输出 JSON（不要 markdown 解释），schema：
{"needMemory":true|false,"memoryTypes":["PROFILE"|"PREFERENCE"|"SEMANTIC"]}

类型含义：
- PROFILE：身份/画像（姓名、家乡、职业、目标等）
- PREFERENCE：偏好/习惯（颜色、食物、爱好、回答风格等）
- SEMANTIC：叙述性经历/往事（「还记得吗」「我说过」「上次」等）

规则：
1) 纯知识问答（如「什么是 Redis」「如何安装 Docker」）→ needMemory=false，memoryTypes=[]
2) 「我喜欢什么颜色 / 我的爱好」→ PREFERENCE
3) 「我是谁 / 我叫什么 / 我的家乡」→ PROFILE
4) 「你还记得我说过… / 上次那件事」→ SEMANTIC
5) 可同时返回多种类型；无把握时 needMemory=false
$prompt$,
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'memory.retrieve.router.system');

INSERT INTO prompt_template (id, code, name, role, content, version, status, create_time, update_time)
SELECT 13, 'memory.retrieve.router.user', '记忆检索路由用户提示词', 'USER',
       E'用户问句:\n{{query}}',
       1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM prompt_template WHERE code = 'memory.retrieve.router.user');
