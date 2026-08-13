package com.wuji.assistant.vta;

/**
 * 录音分析助手提示词 code 映射（来自 schema/28_vta_prompts.sql）。
 */
public final class VtaPromptCodes {

    private VtaPromptCodes() {
    }

    public static final String TRANSCRIPT_USER = "vta.transcript.user";

    public static final String CUSTOMER_TAG_SYSTEM = "vta.customer_tag.system";
    public static final String SALES_TAG_SYSTEM = "vta.sales_tag.system";
    public static final String CALL_SUMMARY_SYSTEM = "vta.call_summary.system";
    public static final String INTENT_SCORE_SYSTEM = "vta.intent_score.system";
    public static final String AGGREGATE_SYSTEM = "vta.aggregate.system";
}

