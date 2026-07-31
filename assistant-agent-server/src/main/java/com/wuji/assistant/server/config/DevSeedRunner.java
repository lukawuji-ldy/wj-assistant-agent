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
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_config WHERE config_id = ?", Integer.class, "llm_primary");
        if (cnt != null && cnt > 0) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO llm_config
                (id, config_id, name, provider, base_url, api_key_cipher, model, temperature, max_tokens,
                 extra_json, status, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '{}'::jsonb, ?, ?, ?)
                """,
                1L, "llm_primary", "默认主模型", "openai_compatible",
                "https://api.openai.com/v1", "CHANGE_ME", "gpt-4o-mini",
                0.70, 4096, "ACTIVE", now, now);
        log.info("seeded llm_config llm_primary; set WUJI_LLM_API_KEY or update api_key_cipher");
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
}
