package com.wuji.assistant.agent.kbtest;

import java.util.List;

/**
 * 跨组 RRF 融合命中：同一领域内多个改写问句结果之间的 RRF 合并层。
 *
 * @param rank               融合后 1-based 排名
 * @param chunkId            切片 id
 * @param docId              文档 id
 * @param content            正文
 * @param groupRanks         各问句组的 1-based rank，未命中为 null
 * @param groupContributions 各问句组的 RRF 贡献 1/(k+rank)
 * @param rrfScore           原始 RRF 分
 * @param normalizedRrf      归一化 RRF
 * @param businessScore      该命中首次出现时的业务 score
 * @author liudy
 */
public record GroupFusedHit(
        int rank,
        String chunkId,
        String docId,
        String content,
        List<Integer> groupRanks,
        List<Double> groupContributions,
        double rrfScore,
        double normalizedRrf,
        double businessScore
) {
}
