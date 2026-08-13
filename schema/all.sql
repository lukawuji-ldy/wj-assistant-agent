-- =============================================================================
-- 无忌助手 Agent：一键建表（UTF-8），按序 \ir 引入分文件
-- 对齐 docs/database-design.md
-- 用法（仓库根目录）：
--   psql "postgresql://postgres:PASSWORD@127.0.0.1:5432/vector_test" -f schema/all.sql
-- =============================================================================

\ir 00_extensions.sql
\ir 01_sys_user.sql
\ir 02_conversation.sql
\ir 03_chat_message.sql
\ir 04_user_profile.sql
\ir 05_memory_extract_log.sql
\ir 06_llm_config.sql
\ir 07_prompt_template.sql
\ir 20_prompt_template_version.sql
\ir 08_llm_call_log.sql
\ir 09_kb_document.sql
\ir 10_kb_document_version.sql
\ir 11_kb_citation_snapshot.sql
\ir 12_vector_store.sql
\ir 13_user_semantic_memory.sql
\ir 14_checkpoint.sql
\ir 15_memory_extract_prompt.sql
\ir 16_memory_retrieve_prompt.sql
\ir 17_memory_retrieve_router_prompt.sql
\ir 27_rag_prompts.sql
\ir 28_vta_prompts.sql
\ir 29_vta_analysis_job.sql
\ir 18_admin_user.sql
\ir 19_admin_audit_log.sql
\ir 21_kb_chunk.sql
\ir 22_kb_chunk_revision.sql
\ir 26_mcp_tool_binding.sql
-- 可选开发种子（leave policy chunk）；生产可跳过
-- \ir 25_kb_chunk_seed_leave_policy.sql
