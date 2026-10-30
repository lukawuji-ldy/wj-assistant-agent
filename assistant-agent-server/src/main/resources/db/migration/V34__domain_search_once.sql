-- 领域子 Agent 每次只调用一次 search_domain，query 使用用户原问题。

UPDATE prompt_template
SET content = '只根据本领域检索结果回答，不要编造。需要证据时只调用一次 search_domain，query 使用用户原问题。',
    update_time = NOW()
WHERE code = 'rag.domain_agent.system';

UPDATE prompt_template_version
SET content = '只根据本领域检索结果回答，不要编造。需要证据时只调用一次 search_domain，query 使用用户原问题。'
WHERE code = 'rag.domain_agent.system';
