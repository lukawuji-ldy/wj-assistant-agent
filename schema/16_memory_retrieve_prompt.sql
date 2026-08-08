-- 长期记忆入模护栏 + 偏好叶子 key（空库 / 手工 schema 落库时同步升级）
-- 禁止原地只改 content：内容变更时追加 PUBLISHED 版本并刷新主表

CREATE OR REPLACE FUNCTION _seed_publish_prompt(
    p_code VARCHAR,
    p_name VARCHAR,
    p_role VARCHAR,
    p_content TEXT,
    p_note VARCHAR
) RETURNS VOID AS $$
DECLARE
    v_cur_content TEXT;
    v_cur_ver INT;
    v_next_ver INT;
    v_id BIGINT;
    v_now TIMESTAMPTZ := NOW();
BEGIN
    SELECT content, published_version INTO v_cur_content, v_cur_ver
    FROM prompt_template WHERE code = p_code;
    IF NOT FOUND THEN
        RETURN;
    END IF;
    IF v_cur_content IS NOT DISTINCT FROM p_content THEN
        RETURN;
    END IF;

    UPDATE prompt_template_version
    SET status = 'SUPERSEDED'
    WHERE code = p_code AND status = 'PUBLISHED';

    v_next_ver := v_cur_ver + 1;
    v_id := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000000)::BIGINT
            + (random() * 1000)::INT;

    INSERT INTO prompt_template_version
        (id, code, version, name, role, content, status, change_note, created_by, create_time, publish_time)
    VALUES
        (v_id, p_code, v_next_ver, p_name, p_role, p_content, 'PUBLISHED', p_note, 'system', v_now, v_now);

    UPDATE prompt_template
    SET name = p_name,
        role = p_role,
        content = p_content,
        published_version = v_next_ver,
        status = 'ACTIVE',
        update_time = v_now
    WHERE code = p_code;
END;
$$ LANGUAGE plpgsql;

SELECT _seed_publish_prompt(
    'agent.default.system',
    '默认系统提示词',
    'SYSTEM',
    $prompt$
你是企业智能助手，回答需准确、可引用知识库时必须标注来源；无可靠知识时不要编造制度结论。
用户问「我喜欢什么 / 我的…」时，「我」指终端用户而非助手。若上下文含「已知用户长期记忆」，必须据此直接回答用户偏好或画像；禁止回答「助手没有个人偏好」，禁止把用户问题理解成询问助手自身。
$prompt$,
    'schema16 memory guard'
);

SELECT _seed_publish_prompt(
    'memory.extract.system',
    '记忆抽取系统提示词',
    'SYSTEM',
    $prompt$
你是企业助手的长期记忆抽取器。根据「用户原文」与「助手回复」产出可落库的 Memory Action 列表。
只输出 JSON（不要 markdown 解释），schema：
{"actions":[{"action":"INSERT|UPDATE|MERGE|DELETE|IGNORE","type":"PROFILE|PREFERENCE|SEMANTIC|NONE","key":"...","newValue":"...","content":"...","confidence":0.0,"importance":0.0,"reason":"..."}]}

分流（强制）：
1) 有稳定 memory_key 且值可独立理解、可覆盖演进 → type=PROFILE 或 PREFERENCE（写 user_profile）
2) 有长期价值但只能靠语义理解/召回 → type=SEMANTIC，必须填 content（写 user_semantic_memory）
3) 无长期价值（闲聊、一次性）→ IGNORE

键值约定示例：display_name（仅「我叫」）、hometown、residence、self_desc、occupation、goal.current、preference.*；禁止用「我是」写 display_name。
偏好叶子 key（强制）：preference.favorite_color、preference.food、preference.hobby.<slug>（如 preference.hobby.football / xiangqi / badminton / table_tennis）；禁止裸 preference.hobby；禁止随意用 preference.sport 与 hobby 混用。
newValue 须短且自包含，勿加「用户」前缀（如「喜欢踢足球」而非「用户喜欢足球」）。
同一句话可拆多条 Action；禁止把整段对话原文入库；禁止写入企业知识库内容。

约束（强制）：
- 条件/未来/学习中（希望、以后、打算、正在学）不得写成无修饰的绝对事实 key（如裸 tech.language）
- newValue / content 须离开原句仍可理解（自包含）；宁可写完整短语，禁止裸值如单独「Java」充当主语言
- 能用 goal.current 表达完整意图时不要再拆绝对 tech.language
- 弱模态显著降低 confidence，或只写 SEMANTIC
- 无把握时 IGNORE
$prompt$,
    'schema16 preference leaf keys'
);

DROP FUNCTION IF EXISTS _seed_publish_prompt(VARCHAR, VARCHAR, VARCHAR, TEXT, VARCHAR);
