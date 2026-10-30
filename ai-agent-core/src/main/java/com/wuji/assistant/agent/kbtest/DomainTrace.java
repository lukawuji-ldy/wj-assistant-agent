package com.wuji.assistant.agent.kbtest;

import java.util.List;

/**
 * 一个领域的完整轨迹：各改写问句轨迹 + 跨组 RRF 层（多问句时才有）。
 *
 * @param domain     领域名
 * @param queries    各改写问句的检索轨迹
 * @param groupFused 跨组 RRF 融合结果；单问句时为空
 * @author liudy
 */
public record DomainTrace(String domain, List<DomainQueryTrace> queries, List<GroupFusedHit> groupFused) {
}
