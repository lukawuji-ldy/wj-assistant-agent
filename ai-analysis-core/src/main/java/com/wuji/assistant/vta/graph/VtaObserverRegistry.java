package com.wuji.assistant.vta.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.wuji.assistant.vta.VtaAnalysisObserver;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Graph 状态会 Jackson 克隆，observer 不能进 OverAllState；按 jobId 旁路注册。
 */
public final class VtaObserverRegistry {

    private static final ConcurrentHashMap<String, VtaAnalysisObserver> OBSERVERS = new ConcurrentHashMap<>();

    private VtaObserverRegistry() {
    }

    public static void register(String jobId, VtaAnalysisObserver observer) {
        if (jobId == null || observer == null) {
            return;
        }
        OBSERVERS.put(jobId, observer);
    }

    public static void clear(String jobId) {
        if (jobId != null) {
            OBSERVERS.remove(jobId);
        }
    }

    public static void notify(String jobId, String nodeName, JsonNode parsedJson) {
        if (jobId == null) {
            return;
        }
        VtaAnalysisObserver observer = OBSERVERS.get(jobId);
        if (observer != null) {
            observer.onNodeDone(nodeName, parsedJson);
        }
    }
}
