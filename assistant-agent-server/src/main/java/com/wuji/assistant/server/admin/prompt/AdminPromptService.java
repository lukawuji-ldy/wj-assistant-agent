package com.wuji.assistant.server.admin.prompt;

import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.server.admin.audit.AdminAuditDetail;
import com.wuji.assistant.server.admin.audit.AdminAuditLogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 管理台提示词版本管理（主表线上副本 + prompt_template_version 历史）。
 *
 * @author liudy
 */
@Service
public class AdminPromptService {

    private static final Set<String> ROLES = Set.of("SYSTEM", "USER");
    private static final int CHANGE_NOTE_MAX = 512;

    private static final RowMapper<AdminPromptVersionView> VERSION_MAPPER = (rs, rowNum) -> {
        Timestamp createTs = rs.getTimestamp("create_time");
        Timestamp publishTs = rs.getTimestamp("publish_time");
        return new AdminPromptVersionView(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("role"),
                rs.getString("content"),
                rs.getInt("version"),
                rs.getString("status"),
                rs.getString("change_note"),
                rs.getString("created_by"),
                createTs == null ? null : createTs.toInstant(),
                publishTs == null ? null : publishTs.toInstant()
        );
    };

    private final JdbcTemplate jdbcTemplate;
    private final PromptTemplateService promptTemplateService;
    private final AdminAuditLogRepository auditLogRepository;

    public AdminPromptService(
            JdbcTemplate jdbcTemplate,
            PromptTemplateService promptTemplateService,
            AdminAuditLogRepository auditLogRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.promptTemplateService = promptTemplateService;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 按 code 聚合列表。
     */
    public List<AdminPromptSummary> listSummaries() {
        return jdbcTemplate.query("""
                SELECT
                  COALESCE(m.code, d.code) AS code,
                  COALESCE(m.name, d.name) AS name,
                  COALESCE(m.role, d.role) AS role,
                  m.published_version AS published_version,
                  d.version AS draft_version,
                  (d.version IS NOT NULL) AS has_draft,
                  m.status AS status,
                  COALESCE(
                    (SELECT MAX(v.version) FROM prompt_template_version v
                     WHERE v.code = COALESCE(m.code, d.code)),
                    0
                  ) AS latest_version
                FROM prompt_template m
                FULL OUTER JOIN (
                  SELECT code, name, role, version
                  FROM prompt_template_version
                  WHERE status = 'DRAFT'
                ) d ON m.code = d.code
                ORDER BY COALESCE(m.code, d.code) ASC
                """, (rs, rowNum) -> {
            Integer published = rs.getObject("published_version") == null ? null : rs.getInt("published_version");
            Integer draft = rs.getObject("draft_version") == null ? null : rs.getInt("draft_version");
            return new AdminPromptSummary(
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("role"),
                    published,
                    draft,
                    rs.getBoolean("has_draft"),
                    rs.getString("status"),
                    rs.getInt("latest_version")
            );
        });
    }

    /**
     * 某 code 全部版本（版本表）。
     */
    public List<AdminPromptVersionView> listVersions(String code) {
        requireCode(code);
        List<AdminPromptVersionView> list = jdbcTemplate.query("""
                SELECT id, code, name, role, content, version, status,
                       change_note, created_by, create_time, publish_time
                FROM prompt_template_version
                WHERE code = ?
                ORDER BY version DESC
                """, VERSION_MAPPER, code);
        if (list.isEmpty()) {
            throw new WujiException(ErrorCode.NOT_FOUND, "提示词不存在: " + code);
        }
        return list;
    }

    /**
     * 两版 Diff（只读）。
     */
    public AdminPromptDiffView diff(String code, int fromVersion, int toVersion) {
        requireCode(code);
        AdminPromptVersionView from = requireVersion(code, fromVersion);
        AdminPromptVersionView to = requireVersion(code, toVersion);
        return new AdminPromptDiffView(code, from, to);
    }

    /**
     * 保存草稿；{@code publish=true} 时保存后立即发布。首个 code 且无主表时，发布后才出现在热路径。
     */
    @Transactional
    public AdminPromptVersionView saveDraft(AdminAuthUser admin, String code, AdminPromptVersionCreateRequest request) {
        requireCode(code);
        requireText(request.getName(), "name");
        requireText(request.getContent(), "content");
        String role = requireRole(request.getRole());
        String name = request.getName().trim();
        String content = request.getContent();
        String changeNote = normalizeChangeNote(request.getChangeNote());
        String adminId = admin.adminId();
        Timestamp now = Timestamp.from(Instant.now());

        AdminPromptVersionView draft = findDraft(code);
        AdminPromptVersionView result;
        if (draft == null) {
            int nextVersion = nextVersion(code);
            long id = IdGenerator.nextLong();
            jdbcTemplate.update("""
                    INSERT INTO prompt_template_version
                    (id, code, version, name, role, content, status, change_note, created_by, create_time, publish_time)
                    VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, NULL)
                    """, id, code, nextVersion, name, role, content, changeNote, adminId, now);
            result = new AdminPromptVersionView(
                    id, code, name, role, content, nextVersion, "DRAFT",
                    changeNote, adminId, now.toInstant(), null);
            auditLogRepository.insert(adminId, "CREATE_DRAFT", "PROMPT_TEMPLATE", code,
                    AdminAuditDetail.builder()
                            .created("version", nextVersion)
                            .created("name", name)
                            .created("role", role)
                            .created("content", content)
                            .created("changeNote", changeNote)
                            .build());
        } else {
            jdbcTemplate.update("""
                    UPDATE prompt_template_version
                    SET name = ?, role = ?, content = ?, change_note = ?, created_by = ?, create_time = ?
                    WHERE code = ? AND status = 'DRAFT'
                    """, name, role, content, changeNote, adminId, now, code);
            result = new AdminPromptVersionView(
                    draft.id(), code, name, role, content, draft.version(), "DRAFT",
                    changeNote, adminId, now.toInstant(), null);
            auditLogRepository.insert(adminId, "UPDATE_DRAFT", "PROMPT_TEMPLATE", code,
                    AdminAuditDetail.builder()
                            .change("name", draft.name(), name)
                            .change("role", draft.role(), role)
                            .change("content", draft.content(), content)
                            .change("changeNote", draft.changeNote(), changeNote)
                            .meta("version", draft.version())
                            .build());
        }

        if (request.isPublish()) {
            return publishInternal(admin, code, result.version(), false);
        }
        return result;
    }

    /**
     * 发布指定 DRAFT。
     */
    @Transactional
    public AdminPromptVersionView publish(AdminAuthUser admin, String code, int version) {
        return publishInternal(admin, code, version, false);
    }

    /**
     * 回滚：复活目标历史版本号（SUPERSEDED→PUBLISHED），不新建 version。
     */
    @Transactional
    public AdminPromptVersionView rollback(AdminAuthUser admin, String code, int version) {
        requireCode(code);
        AdminPromptVersionView source = requireVersion(code, version);
        if ("DRAFT".equals(source.status())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "不能回滚到草稿版本，请直接发布");
        }
        if ("PUBLISHED".equals(source.status())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "目标版本已是当前发布版");
        }
        Integer previousPublished = findPublishedVersion(code);
        if (previousPublished != null && previousPublished == version) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "目标版本已是当前发布版");
        }

        Timestamp now = Timestamp.from(Instant.now());
        String adminId = admin.adminId();

        jdbcTemplate.update("""
                UPDATE prompt_template_version SET status = 'SUPERSEDED'
                WHERE code = ? AND status = 'PUBLISHED'
                """, code);
        // 丢弃未发布草稿，避免与线上版本并存混淆
        jdbcTemplate.update("""
                DELETE FROM prompt_template_version WHERE code = ? AND status = 'DRAFT'
                """, code);

        jdbcTemplate.update("""
                UPDATE prompt_template_version
                SET status = 'PUBLISHED', publish_time = ?
                WHERE code = ? AND version = ?
                """, now, code, version);

        upsertMain(code, source.name(), source.role(), source.content(), version, now);
        promptTemplateService.invalidate(code);

        auditLogRepository.insert(adminId, "ROLLBACK", "PROMPT_TEMPLATE", code,
                AdminAuditDetail.builder()
                        .change("publishedVersion", previousPublished, version)
                        .meta("reactivatedVersion", version)
                        .build());

        return new AdminPromptVersionView(
                source.id(), code, source.name(), source.role(), source.content(), version, "PUBLISHED",
                source.changeNote(), source.createdBy(), source.createTime(), now.toInstant());
    }

    private AdminPromptVersionView publishInternal(AdminAuthUser admin, String code, int version, boolean fromSave) {
        requireCode(code);
        AdminPromptVersionView draft = requireVersion(code, version);
        if (!"DRAFT".equals(draft.status())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "只能发布 DRAFT 版本: " + code + "@" + version);
        }

        Integer previousPublished = findPublishedVersion(code);
        Timestamp now = Timestamp.from(Instant.now());
        String adminId = admin.adminId();

        jdbcTemplate.update("""
                UPDATE prompt_template_version SET status = 'SUPERSEDED'
                WHERE code = ? AND status = 'PUBLISHED'
                """, code);
        jdbcTemplate.update("""
                UPDATE prompt_template_version
                SET status = 'PUBLISHED', publish_time = ?, created_by = ?
                WHERE code = ? AND version = ?
                """, now, adminId, code, version);

        upsertMain(code, draft.name(), draft.role(), draft.content(), version, now);
        promptTemplateService.invalidate(code);

        auditLogRepository.insert(adminId, "PUBLISH", "PROMPT_TEMPLATE", code,
                AdminAuditDetail.builder()
                        .change("publishedVersion", previousPublished, version)
                        .meta("fromSave", fromSave)
                        .created("changeNote", draft.changeNote())
                        .build());

        return new AdminPromptVersionView(
                draft.id(), code, draft.name(), draft.role(), draft.content(), version, "PUBLISHED",
                draft.changeNote(), adminId, draft.createTime(), now.toInstant());
    }

    private void upsertMain(String code, String name, String role, String content, int publishedVersion, Timestamp now) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prompt_template WHERE code = ?", Integer.class, code);
        if (cnt != null && cnt > 0) {
            jdbcTemplate.update("""
                    UPDATE prompt_template
                    SET name = ?, role = ?, content = ?, published_version = ?, status = 'ACTIVE', update_time = ?
                    WHERE code = ?
                    """, name, role, content, publishedVersion, now, code);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO prompt_template
                (id, code, name, role, content, published_version, status, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """, IdGenerator.nextLong(), code, name, role, content, publishedVersion, now, now);
    }

    private AdminPromptVersionView findDraft(String code) {
        List<AdminPromptVersionView> list = jdbcTemplate.query("""
                SELECT id, code, name, role, content, version, status,
                       change_note, created_by, create_time, publish_time
                FROM prompt_template_version
                WHERE code = ? AND status = 'DRAFT'
                """, VERSION_MAPPER, code);
        return list.isEmpty() ? null : list.get(0);
    }

    private AdminPromptVersionView requireVersion(String code, int version) {
        List<AdminPromptVersionView> found = jdbcTemplate.query("""
                SELECT id, code, name, role, content, version, status,
                       change_note, created_by, create_time, publish_time
                FROM prompt_template_version WHERE code = ? AND version = ?
                """, VERSION_MAPPER, code, version);
        if (found.isEmpty()) {
            throw new WujiException(ErrorCode.NOT_FOUND, "提示词版本不存在: " + code + "@" + version);
        }
        return found.get(0);
    }

    private int nextVersion(String code) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT MAX(version) FROM prompt_template_version WHERE code = ?", Integer.class, code);
        return max == null ? 1 : max + 1;
    }

    private Integer findPublishedVersion(String code) {
        List<Integer> list = jdbcTemplate.query(
                "SELECT published_version FROM prompt_template WHERE code = ?",
                (rs, rowNum) -> rs.getInt("published_version"),
                code);
        return list.isEmpty() ? null : list.get(0);
    }

    private static String normalizeChangeNote(String note) {
        if (!StringUtils.hasText(note)) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.length() > CHANGE_NOTE_MAX) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "changeNote 最长 " + CHANGE_NOTE_MAX);
        }
        return trimmed;
    }

    private static void requireCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "code 不能为空");
        }
    }

    private static String requireRole(String role) {
        if (!StringUtils.hasText(role) || !ROLES.contains(role.trim().toUpperCase())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "role 须为 SYSTEM 或 USER");
        }
        return role.trim().toUpperCase();
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, field + " 不能为空");
        }
    }
}
