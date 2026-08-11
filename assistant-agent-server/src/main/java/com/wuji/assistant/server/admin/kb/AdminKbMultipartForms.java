package com.wuji.assistant.server.admin.kb;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * WebFlux multipart 表单字段绑定：从 {@link Part} 读取，避免仅靠 {@code @RequestParam}
 * 时 contentType 等字段丢失。
 *
 * @author liudy
 */
final class AdminKbMultipartForms {

    private AdminKbMultipartForms() {
    }

    static FilePart requireFile(MultiValueMap<String, Part> parts) {
        Part part = parts == null ? null : parts.getFirst("file");
        if (part instanceof FilePart filePart) {
            return filePart;
        }
        throw new WujiException(ErrorCode.BAD_REQUEST, "缺少上传文件 file");
    }

    static AdminKbIngestForm toIngestForm(MultiValueMap<String, Part> parts) {
        return new AdminKbIngestForm(
                field(parts, "title"),
                field(parts, "collection"),
                field(parts, "docId"),
                AdminKbService.parseAclRoles(field(parts, "aclRoles")),
                field(parts, "contentType"),
                field(parts, "preset"),
                intField(parts, "chunkSize"),
                intField(parts, "overlap"),
                intField(parts, "minChunkLengthToKeep"),
                boolField(parts, "chapterSplitEnabled"),
                field(parts, "chapterPattern"),
                field(parts, "sectionTitleMode"),
                field(parts, "separators"),
                field(parts, "keepSeparator"),
                boolField(parts, "normalizeNewlines"),
                boolField(parts, "stripPageNumbers"),
                boolField(parts, "mergeCjkHardWrap"),
                boolField(parts, "collapseBlankLines"),
                boolField(parts, "trimOutsideChapters"),
                field(parts, "trailingNoiseMarkers"));
    }

    static AdminKbIngestForm toPreviewForm(MultiValueMap<String, Part> parts) {
        return new AdminKbIngestForm(
                null, null, null, List.of(),
                field(parts, "contentType"),
                field(parts, "preset"),
                intField(parts, "chunkSize"),
                intField(parts, "overlap"),
                intField(parts, "minChunkLengthToKeep"),
                boolField(parts, "chapterSplitEnabled"),
                field(parts, "chapterPattern"),
                field(parts, "sectionTitleMode"),
                field(parts, "separators"),
                field(parts, "keepSeparator"),
                boolField(parts, "normalizeNewlines"),
                boolField(parts, "stripPageNumbers"),
                boolField(parts, "mergeCjkHardWrap"),
                boolField(parts, "collapseBlankLines"),
                boolField(parts, "trimOutsideChapters"),
                field(parts, "trailingNoiseMarkers"));
    }

    static String field(MultiValueMap<String, Part> parts, String name) {
        if (parts == null || !StringUtils.hasText(name)) {
            return null;
        }
        Part part = parts.getFirst(name);
        if (part instanceof FormFieldPart formField) {
            String v = formField.value();
            return StringUtils.hasText(v) ? v : null;
        }
        return null;
    }

    static Integer intField(MultiValueMap<String, Part> parts, String name) {
        String raw = field(parts, name);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            throw new WujiException(ErrorCode.BAD_REQUEST, name + " 必须是整数");
        }
    }

    static Boolean boolField(MultiValueMap<String, Part> parts, String name) {
        String raw = field(parts, name);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return Boolean.valueOf(raw.trim());
    }
}
