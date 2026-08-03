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
 * 本地开发种子：预置 admin 用户、llm_config、prompt_template（已存在则跳过）。
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
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prompt_template WHERE code = ?", Integer.class, "agent.default.system");
        if (cnt != null && cnt > 0) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO prompt_template (id, code, name, role, content, version, status, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                1L, "agent.default.system", "默认系统提示词", "SYSTEM",
                "你是企业智能助手，回答需准确、可引用知识库时必须标注来源；无可靠知识时不要编造制度结论。",
                1, "ACTIVE", now, now);
        jdbcTemplate.update("""
                INSERT INTO prompt_template (id, code, name, role, content, version, status, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                2L, "agent.default.user", "默认用户提示词模板", "USER",
                "{{message}}", 1, "ACTIVE", now, now);
        log.info("seeded prompt_template defaults");
    }

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
