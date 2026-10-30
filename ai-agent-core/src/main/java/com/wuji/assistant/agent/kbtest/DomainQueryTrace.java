package com.wuji.assistant.agent.kbtest;

import com.wuji.assistant.rag.diagnose.RetrievalTrace;

/**
 * 单个领域、单个改写问句的检索轨迹。
 *
 * @param domain 领域名
 * @param query  实际执行的检索问句（可能来自 query rewrite）
 * @param trace  检索诊断轨迹
 * @author liudy
 */
public record DomainQueryTrace(String domain, String query, RetrievalTrace trace) {
}
