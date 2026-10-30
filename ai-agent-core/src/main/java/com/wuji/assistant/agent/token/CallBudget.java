package com.wuji.assistant.agent.token;

/**
 * 一次模型调用的预算数字。放在 RunnableConfig metadata 里，入模时写入当前消息计数，子 Agent 读取剩余预算作为输出上限。
 *
 * @author liudy
 */
public final class CallBudget {

    public static final String META_KEY = "wuji.contextBudget";

    private int inputBudget;
    private int toolSchemaTokens;
    private int outputCap;
    private int messageTokens;

    public CallBudget(int inputBudget, int toolSchemaTokens, int outputCap) {
        reset(inputBudget, toolSchemaTokens, outputCap);
    }

    public synchronized void reset(int inputBudget, int toolSchemaTokens, int outputCap) {
        this.inputBudget = Math.max(0, inputBudget);
        this.toolSchemaTokens = Math.max(0, toolSchemaTokens);
        this.outputCap = Math.max(0, outputCap);
        this.messageTokens = 0;
    }

    public synchronized int inputBudget() {
        return inputBudget;
    }

    public synchronized int toolSchemaTokens() {
        return toolSchemaTokens;
    }

    /**
     * 大于 0 时，本次模型调用的输出上限不超过该值。
     */
    public synchronized int outputCap() {
        return outputCap;
    }

    public synchronized void setMessageTokens(int messageTokens) {
        this.messageTokens = Math.max(0, messageTokens);
    }

    /**
     * {@code (W - R) - 当前消息 - 工具 schema}。
     */
    public synchronized int remaining() {
        return inputBudget - messageTokens - toolSchemaTokens;
    }
}
