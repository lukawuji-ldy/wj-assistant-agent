package com.wuji.assistant.vta;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 录音分析执行过程回调，用于 SSE 节点完成事件流式推送。
 */
public interface VtaAnalysisObserver {

    /**
     * 单节点完成（包含解析后结构化结果，或解析失败的 error JSON）。
     *
     * @param nodeName   节点名：customerTag / salesTag / callSummary / intentScore / aggregate
     * @param parsedJson 结构化输出 JSON（或包含 error 的 JSON 对象）
     */
    void onNodeDone(String nodeName, JsonNode parsedJson);
}

