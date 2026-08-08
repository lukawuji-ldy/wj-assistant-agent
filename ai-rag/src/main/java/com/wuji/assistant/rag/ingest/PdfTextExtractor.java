package com.wuji.assistant.rag.ingest;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * PDF → 纯文本（PDFBox）。
 *
 * @author liudy
 */
@Component
public class PdfTextExtractor {

    /**
     * @param bytes PDF 字节
     * @return 抽取的纯文本
     */
    public String extract(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "PDF 内容为空");
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return strip(document);
        } catch (InvalidPasswordException e) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "PDF 已加密，无法解析", e);
        } catch (IOException e) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "PDF 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * @param in PDF 流（由调用方关闭外层资源）
     * @return 纯文本
     */
    public String extract(InputStream in) {
        if (in == null) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "PDF 内容为空");
        }
        try {
            return extract(in.readAllBytes());
        } catch (IOException e) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "PDF 读取失败: " + e.getMessage(), e);
        }
    }

    private static String strip(PDDocument document) throws IOException {
        if (document.isEncrypted()) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "PDF 已加密，无法解析");
        }
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(document);
        if (!StringUtils.hasText(text)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "PDF 未提取到有效文本");
        }
        return text;
    }
}
