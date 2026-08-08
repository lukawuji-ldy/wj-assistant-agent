package com.wuji.assistant.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.rag.ingest.ContentHashes;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * 本地开发种子：预置 admin 用户、llm_config、prompt_template（Prompt 按 code 发布新版本升级）。
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
        seedAdminUser();
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

    /**
     * 后台运营账号（admin_user），与聊天 sys_user.admin 同名不同表。
     */
    private void seedAdminUser() {
        Timestamp now = Timestamp.from(Instant.now());
        String hash = passwordEncoder.encode("admin123");
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_user WHERE username = ?", Integer.class, "admin");
        if (cnt != null && cnt > 0) {
            jdbcTemplate.update(
                    "UPDATE admin_user SET password_hash = ?, update_time = ? WHERE username = ? AND is_builtin = TRUE",
                    hash, now, "admin");
            log.info("refreshed local admin_user password hash (console admin)");
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO admin_user
                (id, admin_id, username, password_hash, display_name, role, status, is_builtin, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                1L, "a_admin", "admin", hash, "超级管理员", "SUPER_ADMIN", "ACTIVE", true, now, now);
        log.info("seeded admin_user admin (local password admin123; change in production)");
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
        upsertPrompt(12L, "memory.retrieve.router.system", "记忆检索路由系统提示词", "SYSTEM",
                MEMORY_RETRIEVE_ROUTER_SYSTEM, now);
        upsertPrompt(13L, "memory.retrieve.router.user", "记忆检索路由用户提示词", "USER",
                MEMORY_RETRIEVE_ROUTER_USER, now);
    }

    /**
     * 不存在则插入主表 + PUBLISHED v1；已存在且 content 不同则追加新 PUBLISHED 版本并刷新主表。
     */
    private void upsertPrompt(long id, String code, String name, String role, String content, Timestamp now) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prompt_template WHERE code = ?", Integer.class, code);
        if (cnt == null || cnt == 0) {
            jdbcTemplate.update("""
                    INSERT INTO prompt_template
                    (id, code, name, role, content, published_version, status, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id, code, name, role, content, 1, "ACTIVE", now, now);
            jdbcTemplate.update("""
                    INSERT INTO prompt_template_version
                    (id, code, version, name, role, content, status, change_note, created_by, create_time, publish_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id, code, 1, name, role, content, "PUBLISHED", "seed", "system", now, now);
            log.info("seeded prompt_template {}", code);
            return;
        }

        String current = jdbcTemplate.query("""
                SELECT content FROM prompt_template WHERE code = ?
                """, (rs, rowNum) -> rs.getString("content"), code).stream().findFirst().orElse(null);
        if (content.equals(current)) {
            return;
        }

        Integer maxVer = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM prompt_template_version WHERE code = ?",
                Integer.class, code);
        int nextVersion = maxVer == null ? 1 : maxVer + 1;
        long verId = IdGenerator.nextLong();

        jdbcTemplate.update("""
                UPDATE prompt_template_version SET status = 'SUPERSEDED'
                WHERE code = ? AND status = 'PUBLISHED'
                """, code);
        jdbcTemplate.update("""
                INSERT INTO prompt_template_version
                (id, code, version, name, role, content, status, change_note, created_by, create_time, publish_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                verId, code, nextVersion, name, role, content, "PUBLISHED", "seed-upgrade", "system", now, now);
        jdbcTemplate.update("""
                UPDATE prompt_template
                SET content = ?, name = ?, role = ?, published_version = ?, status = 'ACTIVE', update_time = ?
                WHERE code = ?
                """, content, name, role, nextVersion, now, code);
        log.info("upgraded prompt_template {} -> v{}", code, nextVersion);
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

    private static final String MEMORY_RETRIEVE_ROUTER_SYSTEM = """
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
            """;

    private static final String MEMORY_RETRIEVE_ROUTER_USER = """
            用户问句:
            {{query}}
            """;

    private void seedRagSample() {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kb_document WHERE doc_id = ?", Integer.class, "doc_leave_policy");
        if (cnt == null || cnt == 0) {
            insertLeavePolicyDocument();
        }
        ensureLeavePolicyChunks();
    }

    private void insertLeavePolicyDocument() {
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
    }

    /**
     * 确保 leave policy 在 kb_chunk / revision 有种子行（embedding 可空 → ILIKE）。
     */
    private void ensureLeavePolicyChunks() {
        if (!tableExists("kb_chunk")) {
            log.warn("kb_chunk 表不存在，跳过 RAG chunk 种子（请先执行 Flyway V15/V16）");
            return;
        }
        Integer chunkCnt = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM kb_chunk WHERE doc_id = ? AND version_id = ?
                """, Integer.class, "doc_leave_policy", 1001L);
        if (chunkCnt != null && chunkCnt > 0) {
            return;
        }

        Timestamp now = Timestamp.from(Instant.now());
        String c1 = "Employees may take annual leave up to 15 days per year. Submit requests in HR portal at least 3 days in advance.";
        String c2 = "Sick leave requires a medical certificate when absence exceeds 2 consecutive working days.";
        String h1 = ContentHashes.sha256Hex(c1);
        String h2 = ContentHashes.sha256Hex(c2);
        String id1 = "aaaaaaaa-bbbb-cccc-dddd-111111111111";
        String id2 = "aaaaaaaa-bbbb-cccc-dddd-222222222222";

        jdbcTemplate.update("""
                INSERT INTO kb_chunk
                (chunk_id, version_id, doc_id, collection, chunk_seq, chunk_key,
                 current_revision, section, summary, status, create_time, update_time)
                VALUES (?::uuid, ?, ?, ?, ?, ?, 1, ?, ?, 'ACTIVE', ?, ?)
                ON CONFLICT (chunk_id) DO NOTHING
                """, id1, 1001L, "doc_leave_policy", "kb_default", 1, "doc_leave_policy_v1_c1",
                "annual", summaryOf(c1), now, now);
        jdbcTemplate.update("""
                INSERT INTO kb_chunk_revision (chunk_id, revision, content, content_hash, status, create_time)
                VALUES (?::uuid, 1, ?, ?, 'ACTIVE', ?)
                ON CONFLICT (chunk_id, revision) DO NOTHING
                """, id1, c1, h1, now);
        jdbcTemplate.update("""
                INSERT INTO kb_chunk
                (chunk_id, version_id, doc_id, collection, chunk_seq, chunk_key,
                 current_revision, section, summary, status, create_time, update_time)
                VALUES (?::uuid, ?, ?, ?, ?, ?, 1, ?, ?, 'ACTIVE', ?, ?)
                ON CONFLICT (chunk_id) DO NOTHING
                """, id2, 1001L, "doc_leave_policy", "kb_default", 2, "doc_leave_policy_v1_c2",
                "sick", summaryOf(c2), now, now);
        jdbcTemplate.update("""
                INSERT INTO kb_chunk_revision (chunk_id, revision, content, content_hash, status, create_time)
                VALUES (?::uuid, 1, ?, ?, 'ACTIVE', ?)
                ON CONFLICT (chunk_id, revision) DO NOTHING
                """, id2, c2, h2, now);

        Integer vsCnt = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM vector_store WHERE id IN (?::uuid, ?::uuid)
                """, Integer.class, id1, id2);
        if (vsCnt == null || vsCnt == 0) {
            jdbcTemplate.update("""
                    INSERT INTO vector_store (id, content, metadata, embedding, chunk_seq, ingested_at)
                    VALUES (?::uuid, ?, ?::jsonb, NULL, 1, ?)
                    ON CONFLICT (id) DO NOTHING
                    """,
                    id1, c1,
                    "{\"doc_id\":\"doc_leave_policy\",\"version\":\"v1\",\"version_id\":1001,\"section\":\"annual\",\"chunk_id\":\"doc_leave_policy_v1_c1\",\"status\":\"ACTIVE\",\"collection\":\"kb_default\"}",
                    now);
            jdbcTemplate.update("""
                    INSERT INTO vector_store (id, content, metadata, embedding, chunk_seq, ingested_at)
                    VALUES (?::uuid, ?, ?::jsonb, NULL, 2, ?)
                    ON CONFLICT (id) DO NOTHING
                    """,
                    id2, c2,
                    "{\"doc_id\":\"doc_leave_policy\",\"version\":\"v1\",\"version_id\":1001,\"section\":\"sick\",\"chunk_id\":\"doc_leave_policy_v1_c2\",\"status\":\"ACTIVE\",\"collection\":\"kb_default\"}",
                    now);
        }
        log.info("seeded kb sample doc_leave_policy chunks (kb_chunk + revision; embedding null → ILIKE)");
    }

    private boolean tableExists(String tableName) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?
                """, Integer.class, tableName);
        return n != null && n > 0;
    }

    /** 与 DocumentIngestService.summaryOf 对齐的本地摘要（避免跨包访问包内方法）。 */
    private static String summaryOf(String content) {
        if (content == null) {
            return "";
        }
        String s = content.trim().replace('\n', ' ');
        return s.length() <= 80 ? s : s.substring(0, 80);
    }
}
