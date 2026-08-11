-- V21: 知识库提示词入库；检索规则从 agent.default.system 拆到 rag.answer.system
-- 新版本号必须用 MAX(version)+1，禁止 published_version+1（会与草稿/历史 version 冲突）

INSERT INTO prompt_template (id, code, name, role, content, published_version, status, create_time, update_time)
SELECT 20, 'rag.knowledge_retrieval.system', '知识库检索工具说明', 'SYSTEM',
       $prompt$从已入库知识库检索片段。制度/FAQ/人物经历/产品说明等凡可能被文档回答的问题都必须调用；不要因为问题不像企业制度就跳过。rejected=true 时禁止编造事实。
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
       $prompt$凡用户问题可能被已入库文档回答（制度、FAQ、人物经历、产品说明等），必须先调用 knowledge_retrieval。
仅当工具返回 rejected=true 时，才能说「不在知识库范围内」；禁止仅凭「企业助手」身份、或仅根据其它文档主题（如 CRM）否定库中已有内容。
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

DO $$
DECLARE
    v_cur TEXT;
    v_next INT;
    v_id BIGINT;
    v_new TEXT := $prompt$你是企业智能助手，回答需准确、可引用知识库时必须标注来源；无可靠知识时不要编造制度结论。
用户问「我喜欢什么 / 我的…」时，「我」指终端用户而非助手。若上下文含「已知用户长期记忆」，必须据此直接回答用户偏好或画像；禁止回答「助手没有个人偏好」，禁止把用户问题理解成询问助手自身。
$prompt$;
BEGIN
    SELECT content INTO v_cur FROM prompt_template WHERE code = 'agent.default.system';
    IF NOT FOUND THEN
        RETURN;
    END IF;
    IF btrim(v_cur) IS NOT DISTINCT FROM btrim(v_new) THEN
        RETURN;
    END IF;

    UPDATE prompt_template_version
    SET status = 'SUPERSEDED'
    WHERE code = 'agent.default.system' AND status = 'PUBLISHED';

    SELECT COALESCE(MAX(version), 0) + 1 INTO v_next
    FROM prompt_template_version
    WHERE code = 'agent.default.system';

    v_id := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000000)::BIGINT
            + (random() * 1000)::INT;

    INSERT INTO prompt_template_version
        (id, code, version, name, role, content, status, change_note, created_by, create_time, publish_time)
    SELECT v_id, 'agent.default.system', v_next, COALESCE(p.name, '默认系统提示词'), 'SYSTEM',
           v_new, 'PUBLISHED', 'rag prompts extracted', 'system', NOW(), NOW()
    FROM prompt_template p
    WHERE p.code = 'agent.default.system';

    UPDATE prompt_template
    SET content = v_new,
        published_version = v_next,
        status = 'ACTIVE',
        update_time = NOW()
    WHERE code = 'agent.default.system';
END $$;
