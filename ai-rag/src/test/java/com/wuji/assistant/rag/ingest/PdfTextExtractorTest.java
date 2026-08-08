package com.wuji.assistant.rag.ingest;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDF 文本抽取单测。
 *
 * @author liudy
 */
class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    void extractsTextFromSimplePdf() throws Exception {
        byte[] pdf = buildSimplePdf("Hello Wuji KB");
        String text = extractor.extract(pdf);
        assertTrue(text.contains("Hello Wuji KB"));
    }

    @Test
    void rejectsEmptyBytes() {
        WujiException ex = assertThrows(WujiException.class, () -> extractor.extract(new byte[0]));
        assertTrue(ex.getErrorCode() == ErrorCode.BAD_REQUEST || ex.getMessage().contains("空"));
    }

    @Test
    void rejectsCorruptBytes() {
        WujiException ex = assertThrows(WujiException.class, () -> extractor.extract("not-a-pdf".getBytes()));
        assertTrue(ex.getMessage().contains("PDF") || ex.getErrorCode() == ErrorCode.BAD_REQUEST);
    }

    private static byte[] buildSimplePdf(String content) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(content);
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
