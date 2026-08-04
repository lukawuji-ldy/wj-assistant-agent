package com.wuji.assistant.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * 本地开发种子：预置 admin 用户、llm_config、prompt_template（Prompt 按 code upsert 升级）。
 *
 * @author liudy
 */
@Component
public class DevSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public DevSeedRunner(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedUser();
        seedLlm();
        seedPrompt();
        seedRagSample();
    }

    private void seedUser() {
        Timestamp now = Timestamp.from(Instant.now());
        String hash = passwordEncoder.encode("admin123");
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = ?", Integer.class, "admin");
        if (cnt != null && cnt > 0) {
            jdbcTemplate.update(
                    "UPDATE sys_user SET password_hash = ?, update_time = ? WHERE username = ?",
                    hash, now, "admin");
            log.info("refreshed local admin password hash");
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO sys_user
                (id, user_id, username, password_hash, nickname, tenant_id, role, status, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                1L, "u_admin", "admin", hash, "管理员", "default", "admin", "ACTIVE", now, now);
        log.info("seeded sys_user admin (local password admin123; change in production)");
    }

    private void seedLlm() {
        Timestamp now = Timestamp.from(Instant.now());
        seedChatLlmRow(1L, "llm_primary", "默认主模型", now);
        seedChatLlmRow(2L, "llm_backup_1", "备用模型占位", now);
        seedEmbeddingLlmRow(3L, "llm_embedding", "默认向量模型", now);
    }

    private void seedChatLlmRow(long id, String configId, String name, Timestamp now) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_config WHERE config_id = ?", Integer.class, configId);
        if (cnt != null && cnt > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO llm_config
                (id, config_id, name, provider, model_kind, base_url, api_key_cipher, model, temperature, max_tokens,
                 extra_json, status, create_time, update_time)
                VALUES (?, ?, ?, ?, 'CHAT', ?, ?, ?, ?, ?, '{}'::jsonb, ?, ?, ?)
                """,
                id, configId, name, "openai_compatible",
                "https://api.openai.com/v1", "CHANGE_ME", "gpt-4o-mini",
                0.70, 4096, "ACTIVE", now, now);
        log.info("seeded llm_config {} (CHAT); set WUJI_LLM_API_KEY or update api_key_cipher", configId);
    }

    private void seedEmbeddingLlmRow(long id, String configId, String name, Timestamp now) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_config WHERE config_id = ?", Integer.class, configId);
        if (cnt != null && cnt > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO llm_config
                (id, config_id, name, provider, model_kind, base_url, api_key_cipher, model, temperature, max_tokens,
                 extra_json, status, create_time, update_time)
                VALUES (?, ?, ?, ?, 'EMBEDDING', ?, ?, ?, NULL, NULL,
                        '{"dimensions":1536,"embeddings_path":"/v1/embeddings"}'::jsonb, ?, ?, ?)
                """,
                id, configId, name, "openai_compatible",
                "https://api.openai.com/v1", "CHANGE_ME", "text-embedding-3-small",
                "ACTIVE", now, now);
        log.info("seeded llm_config {} (EMBEDDING); set WUJI_LLM_API_KEY or update api_key_cipher", configId);
    }

    private void seedPrompt() {
        Timestamp now = Timestamp.from(Instant.now());
        upsertPrompt(1L, "agent.default.system", "默认系统提示词", "SYSTEM",
                AGENT_DEFAULT_SYSTEM, now);
        upsertPrompt(2L, "agent.default.user", "默认用户提示词模板", "USER",
                "{{message}}", now);
        upsertPrompt(10L, "memory.extract.system", "记忆抽取系统提示词", "SYSTEM",
                MEMORY_EXTRACT_SYSTEM, now);
        upsertPrompt(11L, "memory.extract.user", "记忆抽取用户提示词", "USER",
                MEMORY_EXTRACT_USER, now);
    }

    /**
     * 不存在则插入；已存在则按 code 覆盖 content（保证本地已有行也能吃到约定升级）。
     */
    private void upsertPrompt(long id, String code, String name, String role, String content, Timestamp now) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prompt_template WHERE code = ?", Integer.class, code);
        if (cnt != null && cnt > 0) {
            jdbcTemplate.update("""
                    UPDATE prompt_template
                    SET content = ?, name = ?, role = ?, version = version + 1,
                        status = 'ACTIVE', update_time = ?
                    WHERE code = ?
                    """, content, name, role, now, code);
            log.info("upgraded prompt_template {}", code);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO prompt_template (id, code, name, role, content, version, status, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, code, name, role, content, 1, "ACTIVE", now, now);
        log.info("seeded prompt_template {}", code);
    }

    private static final String AGENT_DEFAULT_SYSTEM = """
            你是企业智能助手，回答需准确、可引用知识库时必须标注来源；无可靠知识时不要编造制度结论。
            用户问「我喜欢什么 / 我的…」时，「我」指终端用户而非助手。若上下文含「已知用户长期记忆」，必须据此直接回答用户偏好或画像；禁止回答「助手没有个人偏好」，禁止把用户问题理解成询问助手自身。
            """;

    private static final String MEMORY_EXTRACT_SYSTEM = """
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
            """;

    private static final String MEMORY_EXTRACT_USER = """
            用户原文:
            {{user_text}}

            助手回复:
            {{assistant_text}}
            """;

    private void seedRagSample() {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kb_document WHERE doc_id = ?", Integer.class, "doc_leave_policy");
        if (cnt != null && cnt > 0) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO kb_document
                (id, doc_id, collection, title, current_version_id, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                100L, "doc_leave_policy", "kb_default", "Employee Leave Policy", 1001L, now, now);
        jdbcTemplate.update("""
                INSERT INTO kb_document_version
                (id, doc_id, version, status, source, acl_roles, published_at, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, '["admin","user"]'::jsonb, ?, ?, ?)
                """,
                1001L, "doc_leave_policy", "v1", "ACTIVE", "seed://leave-policy", now, now, now);
        jdbcTemplate.update("""
                INSERT INTO vector_store (id, content, metadata, embedding)
                VALUES (?::uuid, ?, ?::jsonb, NULL)
                """,
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeee1",
                "Employees may take annual leave up to 15 days per year. Submit requests in HR portal at least 3 days in advance.",
                "{\"doc_id\":\"doc_leave_policy\",\"version\":1,\"section\":\"annual\",\"chunk_id\":\"c1\",\"status\":\"ACTIVE\",\"collection\":\"kb_default\"}");
        jdbcTemplate.update("""
                INSERT INTO vector_store (id, content, metadata, embedding)
                VALUES (?::uuid, ?, ?::jsonb, NULL)
                """,
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeee2",
                "Sick leave requires a medical certificate when absence exceeds 2 consecutive working days.",
                "{\"doc_id\":\"doc_leave_policy\",\"version\":1,\"section\":\"sick\",\"chunk_id\":\"c2\",\"status\":\"ACTIVE\",\"collection\":\"kb_default\"}");
        log.info("seeded kb sample doc_leave_policy + vector_store chunks (embedding null; cosine when Key available, else ILIKE)");
    }
}
