package com.wuji.assistant.vta.graph;

/**
 * VTA StateGraph 状态键。
 */
public final class VtaGraphKeys {

    public static final String JOB_ID = "jobId";
    public static final String USER_ID = "userId";
    public static final String TRACE_ID = "traceId";
    public static final String TRANSCRIPT = "transcript";
    public static final String USER_PROMPT = "userPrompt";
    public static final String JOB_STATE = "jobState";

    public static final String CUSTOMER_TAG = "customerTag";
    public static final String SALES_TAG = "salesTag";
    public static final String CALL_SUMMARY = "callSummary";
    public static final String INTENT_SCORE = "intentScore";

    public static final String PARTIAL_RAW = "partialRaw";
    public static final String PARTIAL_FAILURE = "partialFailure";
    public static final String AGGREGATE = "aggregate";

    public static final String NODE_VALIDATE = "validateInput";
    public static final String NODE_CUSTOMER = "customerTag";
    public static final String NODE_SALES = "salesTag";
    public static final String NODE_SUMMARY = "callSummary";
    public static final String NODE_INTENT = "intentScore";
    public static final String NODE_MERGE = "mergePartial";
    public static final String NODE_AGGREGATE = "aggregate";

    private VtaGraphKeys() {
    }
}
