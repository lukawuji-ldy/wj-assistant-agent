package com.wuji.assistant.agent.config;

import com.wuji.assistant.rag.ingest.ChineseDocumentPreprocessor;
import com.wuji.assistant.rag.ingest.ChineseRecursiveTextSplitter;
import com.wuji.assistant.rag.ingest.KeepSeparator;
import com.wuji.assistant.rag.ingest.SectionTitleMode;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 将 yml 切分/预处理参数应用到共享 bean。
 *
 * @author liudy
 */
@Component
public class RagSplitterConfigurer {

    private final WujiRagProperties ragProperties;
    private final ChineseRecursiveTextSplitter splitter;
    private final ChineseDocumentPreprocessor preprocessor;

    public RagSplitterConfigurer(WujiRagProperties ragProperties,
                                 ChineseRecursiveTextSplitter splitter,
                                 ChineseDocumentPreprocessor preprocessor) {
        this.ragProperties = ragProperties;
        this.splitter = splitter;
        this.preprocessor = preprocessor;
    }

    @PostConstruct
    public void apply() {
        splitter.setChunkSize(ragProperties.getChunkSize());
        splitter.setOverlap(ragProperties.getChunkOverlap());
        splitter.setMinChunkLengthToKeep(ragProperties.getMinChunkLengthToKeep());
        splitter.setChapterSplitEnabled(ragProperties.isChapterSplitEnabled());
        WujiRagProperties.Splitter sp = ragProperties.getSplitter();
        if (sp != null) {
            splitter.setChapterPattern(sp.getChapterPattern());
            splitter.setSectionTitleMode(SectionTitleMode.parse(sp.getSectionTitleMode()));
            splitter.setSeparators(sp.getSeparators());
            splitter.setKeepSeparator(KeepSeparator.parse(sp.getKeepSeparator()));
        }
        WujiRagProperties.Preprocess pre = ragProperties.getPreprocess();
        if (pre != null) {
            preprocessor.setNormalizeNewlines(pre.isNormalizeNewlines());
            preprocessor.setStripPageNumbers(pre.isStripPageNumbers());
            preprocessor.setMergeCjkHardWrap(pre.isMergeCjkHardWrap());
            preprocessor.setCollapseBlankLines(pre.isCollapseBlankLines());
            preprocessor.setTrimOutsideChapters(pre.isTrimOutsideChapters());
            preprocessor.setTrailingNoiseMarkers(pre.getTrailingNoiseMarkers());
            if (sp != null) {
                preprocessor.setChapterPattern(sp.getChapterPattern());
            }
        }
    }
}
