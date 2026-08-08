package com.wuji.assistant.common.exception;

/**
 * 统一业务错误码。
 *
 * @author liudy
 */
public enum ErrorCode {

    /** 请求参数非法 */
    BAD_REQUEST("BAD_REQUEST", "请求参数非法"),
    /** 未认证 */
    UNAUTHORIZED("UNAUTHORIZED", "未登录或令牌无效"),
    /** 无权限 */
    FORBIDDEN("FORBIDDEN", "无权限"),
    /** 资源不存在 */
    NOT_FOUND("NOT_FOUND", "资源不存在"),
    /** 模型超时 */
    MODEL_TIMEOUT("MODEL_TIMEOUT", "模型调用超时"),
    /** 模型限流 */
    MODEL_RATE_LIMITED("MODEL_RATE_LIMITED", "模型限流"),
    /** 模型不可用 */
    MODEL_UNAVAILABLE("MODEL_UNAVAILABLE", "模型不可用"),
    /** Agent 达到执行上限 */
    AGENT_MAX_ITERATIONS("AGENT_MAX_ITERATIONS", "Agent 达到最大执行次数"),
    /** 流式续传过期 */
    STREAM_EXPIRED("STREAM_EXPIRED", "流式会话已过期"),
    /** MCP 工具失败 */
    MCP_TOOL_FAILED("MCP_TOOL_FAILED", "MCP 工具调用失败"),
    /** RAG 不可用 */
    RAG_UNAVAILABLE("RAG_UNAVAILABLE", "知识库不可用"),
    /** RAG 入库失败 */
    RAG_INGEST_FAILED("RAG_INGEST_FAILED", "知识库入库失败"),
    /** RAG 拒答 */
    RAG_REFUSED("RAG_REFUSED", "无可靠知识命中"),
    /** 记忆加载失败 */
    MEMORY_LOAD_FAILED("MEMORY_LOAD_FAILED", "记忆加载失败"),
    /** 记忆不存在 */
    MEMORY_NOT_FOUND("MEMORY_NOT_FOUND", "记忆不存在"),
    /** ACTIVE 记忆键冲突 */
    MEMORY_KEY_CONFLICT("MEMORY_KEY_CONFLICT", "记忆键已存在"),
    /** 内部错误 */
    INTERNAL_ERROR("INTERNAL_ERROR", "系统内部错误");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
