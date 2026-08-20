package com.wuji.assistant.rag.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内容类型合并后的预处理+切分效果。
 *
 * @author liudy
 */
class DocumentIngestPrepareSplitTest {

    private final ChineseDocumentPreprocessor preprocessor = new ChineseDocumentPreprocessor();
    private final ChineseRecursiveTextSplitter splitter = new ChineseRecursiveTextSplitter();
    private DocumentIngestService ingestService;

    @BeforeEach
    void setUp() {
        // prepareSplit / previewSplit 不访问 JDBC / Embedding
        ingestService = new DocumentIngestService(
                null, new ObjectMapper(), preprocessor, splitter, null, null);
    }

    private SplitPreviewResult previewWithType(String text, String contentType) {
        SplitOptions req = new SplitOptions(null, null, null, null, null, null, null, null, contentType);
        return ingestService.previewSplit(text, req, null);
    }

    @Test
    void faqQaSplitsByQuestionBoundaryNotNarrativeSize() {
        String text = """
                FAQ知识库示例

                ============================== 分类一：账号登录问题
                ==============================

                Q：忘记密码如何处理？

                A：
                如果忘记系统登录密码，可以在登录页面点击“忘记密码”。

                Q：为什么输入正确密码仍然无法登录？

                A：可能原因包括账号被锁定或网络异常。

                ============================== 分类二：客户管理
                ==============================

                Q：如何新增客户信息？

                A：进入CRM系统后选择客户管理菜单。
                """;
        SplitPreviewResult preview = previewWithType(text, ContentTypeCatalog.FAQ_QA);
        assertEquals(ContentTypeCatalog.FAQ_QA, preview.resolvedOptions().get("contentType"));
        assertTrue(preview.chunkCount() >= 3, "应按 Q 边界切出多块，实际=" + preview.chunkCount());
        assertFalse(preview.warnings().stream().anyMatch(w -> w.contains("未检测到章节标题")),
                "FAQ 不应出现叙事章节警告: " + preview.warnings());
        assertTrue(preview.chunks().stream().anyMatch(c -> c.content().contains("忘记密码")
                && c.content().contains("A：")), "首问应答同块");
    }

    @Test
    void missingStrategyStillResolvesViaCatalogNotBeanOnly() {
        SplitOptions empty = new SplitOptions(null, null, null, null, null, null, null, null, null);
        SplitPreviewResult preview = ingestService.previewSplit("一、甲\n短文。\n\n二、乙\n另一段。", empty, null);
        assertEquals(ContentTypeCatalog.NARRATIVE, preview.resolvedOptions().get("contentType"));
    }

    @Test
    void narrativeFullLineAndTrimOutside() {
        String text = """
                前言套话请忽略。

                一、总则标题行
                本章规定请假流程。

                二、年假标题行
                员工每年十五天年假。

                复制上面这段存成文件即可。
                """;
        SplitPreviewResult preview = previewWithType(text, ContentTypeCatalog.NARRATIVE);
        assertTrue(preview.chunkCount() >= 2);
        assertTrue(preview.chunks().stream().anyMatch(c -> c.section().contains("总则")));
        assertTrue(preview.chunks().stream().noneMatch(c -> c.content().contains("复制上面这段")));
        assertTrue(preview.chunks().stream().noneMatch(c -> c.content().contains("前言套话")));
    }

    @Test
    void techMarkdownSplitsByHeading() {
        String text = """
                # 第一章
                内容甲。
                ## 第二节
                内容乙。
                """;
        SplitPreviewResult preview = previewWithType(text, ContentTypeCatalog.TECH_MARKDOWN);
        assertTrue(preview.chunkCount() >= 2);
        assertTrue(preview.chunks().stream().anyMatch(c -> c.section().startsWith("#")));
    }

    @Test
    void sameInputYieldsStableChunkCount() {
        String text = "一、甲\n短文。\n\n二、乙\n另一段。";
        SplitPreviewResult a = previewWithType(text, ContentTypeCatalog.NARRATIVE);
        SplitPreviewResult b = previewWithType(text, ContentTypeCatalog.NARRATIVE);
        assertEquals(a.chunkCount(), b.chunkCount());
    }

    @Test
    void policySplitsByArticle() {
        String text = """
                第八条 违约责任
                甲方应赔偿。

                第九条 争议解决
                提交仲裁。
                """;
        SplitPreviewResult preview = previewWithType(text, ContentTypeCatalog.POLICY_CLAUSE);
        assertTrue(preview.chunkCount() >= 2);
        assertTrue(preview.chunks().stream().anyMatch(c -> c.section().contains("第八条")));
    }

    @Test
    void policyKeepsChapterBodyAndDoesNotGlueNextChapterToPreviousArticle() {
        String text = """
                法律合同知识库示例文档
                知识库名称：企业合同法律知识库

                ============================== 第一章 合同基本信息
                ==============================

                合同名称： 技术服务合同
                合同主体：
                甲方： 某科技有限公司

                ============================== 第二章 服务范围
                ==============================

                第一条 服务内容
                乙方提供软件开发与维护。

                第二条 服务标准
                按约定时间完成工作。

                ============================== 第三章 合同费用与支付
                ==============================

                第三条 服务费用
                合同总金额： 人民币100万元。

                ============================== 第七章 争议解决
                ==============================

                第九条 争议处理
                双方发生争议时，应首先协商解决。

                ============================== 法律合同FAQ抽取
                ==============================

                Q：合同金额是多少？
                A： 合同服务费用总金额为人民币100万元。
                """;
        SplitPreviewResult preview = previewWithType(text, ContentTypeCatalog.POLICY_CLAUSE);
        assertTrue(preview.chunks().stream().anyMatch(c ->
                c.section().contains("第一章") && c.content().contains("合同名称") && c.content().contains("合同主体")),
                "第一章正文应保留");
        assertTrue(preview.chunks().stream().noneMatch(c ->
                c.section() != null && c.section().contains("第二条") && c.content().contains("第三章")),
                "第二章最后一条不应粘上下一章标题");
        assertTrue(preview.chunks().stream().anyMatch(c ->
                c.section().contains("第三章") && c.section().contains("第三条") && c.content().contains("100万")),
                "第三条 section 应带章路径");
        assertTrue(preview.chunks().stream().noneMatch(c ->
                c.section() != null && c.section().contains("第九条") && c.content().contains("Q：")));
        assertTrue(preview.chunks().stream().anyMatch(c -> c.content().contains("Q：合同金额")));
        assertTrue(preview.warnings().isEmpty(), "应识别到章/条边界: " + preview.warnings());
    }
}
