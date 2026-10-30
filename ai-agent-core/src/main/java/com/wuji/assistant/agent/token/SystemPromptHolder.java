package com.wuji.assistant.agent.token;

/**
 * 本轮系统提示。metadata 里放这个对象，压缩后改写对后续模型调用可见。
 *
 * @author liudy
 */
public final class SystemPromptHolder {

    private volatile String prompt = "";

    public SystemPromptHolder() {
    }

    public SystemPromptHolder(String prompt) {
        set(prompt);
    }

    public String get() {
        return prompt == null ? "" : prompt;
    }

    public void set(String prompt) {
        this.prompt = prompt == null ? "" : prompt;
    }
}
