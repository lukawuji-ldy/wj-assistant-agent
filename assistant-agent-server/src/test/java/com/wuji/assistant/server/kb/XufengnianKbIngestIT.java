package com.wuji.assistant.server.kb;

import com.wuji.assistant.rag.ingest.DocumentIngestService;
import com.wuji.assistant.rag.ingest.IngestRequest;
import com.wuji.assistant.rag.ingest.IngestResult;
import com.wuji.assistant.server.AssistantAgentServerApplication;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地手工入库：将 testData/徐凤年经历.txt 同步到企业知识库。
 * <p>
 * 依赖本机 PostgreSQL（application.yml）与可用 Embedding（WUJI_LLM_API_KEY / llm_config）。
 * 去掉 {@link Disabled} 后在 IDE 中单独运行本类即可。
 *
 * @author liudy
 */
@Disabled("本地手工入库：去掉 @Disabled 后单独运行")
@SpringBootTest(classes = AssistantAgentServerApplication.class)
class XufengnianKbIngestIT {

    private static final Logger log = LoggerFactory.getLogger(XufengnianKbIngestIT.class);

    private static final String RESOURCE = "testData/徐凤年经历.txt";
    private static final String DOC_ID = "doc_xufengnian_bio";
    private static final String TITLE = "雪中悍刀行·徐凤年经历";
    private static final String COLLECTION = "kb_default";

    @Autowired
    private DocumentIngestService documentIngestService;

    @Test
    void ingestXufengnianBio() throws Exception {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        assertTrue(content.trim().length() > 0, "测试数据为空: " + RESOURCE);

        IngestResult result = documentIngestService.ingest(new IngestRequest(
                DOC_ID,
                TITLE,
                COLLECTION,
                content,
                RESOURCE
        ));

        log.info("ingested docId={} version={} versionId={} chunks={} embedded={}",
                result.docId(), result.version(), result.versionId(),
                result.chunkCount(), result.embedded());

        assertTrue(result.chunkCount() > 0, "切分后应有切片");
    }
}
