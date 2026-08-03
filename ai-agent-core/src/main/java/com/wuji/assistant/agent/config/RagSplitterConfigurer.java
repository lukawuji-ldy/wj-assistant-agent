package com.wuji.assistant.agent.config;

import com.wuji.assistant.rag.ingest.ChineseRecursiveTextSplitter;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 将 yml 切分参数应用到 {@link ChineseRecursiveTextSplitter}。
 *
 * @author liudy
 */
@Component
public class RagSplitterConfigurer {

    private final WujiRagProperties ragProperties;
    private final ChineseRecursiveTextSplitter splitter;

    public RagSplitterConfigurer(WujiRagProperties ragProperties, ChineseRecursiveTextSplitter splitter) {
        this.ragProperties = ragProperties;
        this.splitter = splitter;
    }

    @PostConstruct
    public void apply() {
        splitter.setChunkSize(ragProperties.getChunkSize());
        splitter.setOverlap(ragProperties.getChunkOverlap());
        splitter.setMinChunkLengthToKeep(ragProperties.getMinChunkLengthToKeep());
        splitter.setChapterSplitEnabled(ragProperties.isChapterSplitEnabled());
    }
}
