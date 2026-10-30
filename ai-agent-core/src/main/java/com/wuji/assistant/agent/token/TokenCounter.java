package com.wuji.assistant.agent.token;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

/**
 * 按某个 llm_config 的词表计数。正文走 {@code TokenEstimator}，消息和工具再加上固定开销。
 *
 * @author liudy
 */
public interface TokenCounter {

    /**
     * @return 词表标识，同一标识下条目 token 可以缓存
     */
    String tokenizerId();

    /**
     * @param text 正文，可空
     * @return token 数
     */
    int countText(String text);

    /**
     * 正文 + 每条消息与角色开销 + 收尾开销。
     *
     * @param messages 消息，可空
     * @return token 数
     */
    int countMessages(List<Message> messages);

    /**
     * name、description 与 inputSchema。
     *
     * @param tools 工具定义，可空
     * @return token 数
     */
    int countTools(List<ToolDefinition> tools);
}
