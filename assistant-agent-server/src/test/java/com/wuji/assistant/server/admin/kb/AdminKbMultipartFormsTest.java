package com.wuji.assistant.server.admin.kb;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * multipart 字段绑定：contentType 必须进入 SplitOptions.preset。
 *
 * @author liudy
 */
class AdminKbMultipartFormsTest {

    @Test
    void previewFormBindsContentTypeIntoPreset() {
        MultiValueMap<String, Part> parts = new LinkedMultiValueMap<>();
        parts.add("contentType", field("contentType", "faq_qa"));
        parts.add("preset", field("preset", "faq_qa"));

        AdminKbIngestForm form = AdminKbMultipartForms.toPreviewForm(parts);
        assertEquals("faq_qa", form.contentType());
        assertEquals("faq_qa", form.toSplitOptions().preset());
        assertNull(form.toSplitOptions().chunkSize());
    }

    @Test
    void ingestFormPrefersContentTypeOverPreset() {
        MultiValueMap<String, Part> parts = new LinkedMultiValueMap<>();
        parts.add("contentType", field("contentType", "faq_qa"));
        parts.add("preset", field("preset", "narrative"));
        parts.add("title", field("title", "t"));

        AdminKbIngestForm form = AdminKbMultipartForms.toIngestForm(parts);
        assertEquals("faq_qa", form.toSplitOptions().preset());
        assertEquals("t", form.title());
        assertEquals(List.of(), form.aclRoles());
    }

    private static FormFieldPart field(String name, String value) {
        return new FormFieldPart() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public HttpHeaders headers() {
                return new HttpHeaders();
            }

            @Override
            public Flux<org.springframework.core.io.buffer.DataBuffer> content() {
                return Flux.empty();
            }
        };
    }
}
