package com.wuji.assistant.agent.stream;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.GraphLifecycleListener;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.OverAllStateBuilder;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.state.ReplaceAllWith;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 用 {@link ReplaceAllWith} 覆盖 checkpoint 里的 messages，避免 AppendStrategy 把历史再追加一遍。
 *
 * @author liudy
 */
public final class CheckpointGraphCaller {

    private CheckpointGraphCaller() {
    }

    /**
     * @param messages 优化器装好的消息，不含 system
     * @param userText 当轮问题，写入 input
     * @return 图输入
     */
    public static Map<String, Object> replaceAllInput(List<Message> messages, String userText) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("messages", ReplaceAllWith.of(messages == null ? List.of() : messages));
        inputs.put("input", userText == null ? "" : userText);
        return inputs;
    }

    /**
     * {@code graph.invoke(Map)} / {@code stream(Map)} 走 {@code withData}，用普通 ObjectMapper 深拷贝。
     * {@link ReplaceAllWith} 的 {@code newValues} 没有元素类型，拷贝后变成 Map。
     * {@code putData} 不拷贝，消息保持原类型交给 AppendStrategy。
     */
    static OverAllState initialState(CompiledGraph graph, List<Message> messages, String userText) {
        OverAllStateBuilder builder = OverAllStateBuilder.builder()
                .withKeyStrategies(graph.getKeyStrategyMap());
        if (graph.compileConfig != null) {
            builder.withStore(graph.compileConfig.getStore());
        }
        replaceAllInput(messages, userText).forEach(builder::putData);
        builder.putData(GraphLifecycleListener.EXECUTION_ID_KEY, UUID.randomUUID().toString());
        return builder.build();
    }

    /**
     * @param agent    已构建的 ReactAgent
     * @param messages 覆盖写入的消息
     * @param userText 当轮问题
     * @param config   含 threadId 的运行配置
     * @return 最后一条助手消息
     */
    public static AssistantMessage invoke(ReactAgent agent, List<Message> messages, String userText,
                                          RunnableConfig config) throws GraphRunnerException {
        CompiledGraph graph = compiled(agent);
        Optional<OverAllState> state = graph.invoke(initialState(graph, messages, userText), config);
        AssistantMessage reply = lastAssistant(state.map(OverAllState::data).orElse(Map.of()));
        if (reply == null) {
            throw new IllegalStateException("No AssistantMessage found in 'messages' state");
        }
        return reply;
    }

    /**
     * 流式节点输出里抽出本轮新出现的助手消息。入参里已有的上一条助手回复不推给页面。
     * 同一条回复的累计文本由 {@link AgentStreamBridge} 收成增量。
     */
    public static Flux<Message> stream(ReactAgent agent, List<Message> messages, String userText,
                                       RunnableConfig config) {
        int baselineCount = assistantCount(messages);
        String baselineText = lastAssistantText(messages);
        CompiledGraph graph = compiled(agent);
        return graph.streamFromInitialNode(initialState(graph, messages, userText), config)
                .handle((NodeOutput output, SynchronousSink<Message> sink) -> {
                    if (output == null || output.state() == null) {
                        return;
                    }
                    Map<String, Object> data = output.state().data();
                    if (!shouldPublish(data, baselineCount, baselineText)) {
                        return;
                    }
                    sink.next(lastAssistant(data));
                });
    }

    /**
     * 节点刚启动时，最后一条助手消息还是上一轮的。条数没增加且正文没变，就不要推。
     */
    static boolean shouldPublish(Map<String, Object> data, int baselineCount, String baselineText) {
        AssistantMessage reply = lastAssistant(data);
        if (reply == null || reply.getText() == null || reply.getText().isEmpty()) {
            return false;
        }
        if (baselineText != null && baselineText.equals(reply.getText()) && assistantCount(data) <= baselineCount) {
            return false;
        }
        return true;
    }

    static int assistantCount(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Message message : messages) {
            if (message instanceof AssistantMessage) {
                count++;
            }
        }
        return count;
    }

    static String lastAssistantText(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        String text = null;
        for (Message message : messages) {
            if (message instanceof AssistantMessage assistant
                    && assistant.getText() != null
                    && !assistant.getText().isEmpty()) {
                text = assistant.getText();
            }
        }
        return text;
    }

    private static int assistantCount(Map<String, Object> data) {
        List<?> list = messageList(data);
        if (list.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Object item : list) {
            if (item instanceof AssistantMessage) {
                count++;
            }
        }
        return count;
    }

    /**
     * {@code getCompiledGraph()} 在首次编译前是 null。官方入口走 {@link ReactAgent#getAndCompileGraph()}。
     */
    private static CompiledGraph compiled(ReactAgent agent) {
        return agent.getAndCompileGraph();
    }

    static AssistantMessage lastAssistant(Map<String, Object> data) {
        List<?> list = messageList(data);
        AssistantMessage last = null;
        for (Object item : list) {
            if (item instanceof AssistantMessage assistant) {
                last = assistant;
            }
        }
        return last;
    }

    private static List<?> messageList(Map<String, Object> data) {
        if (data == null) {
            return List.of();
        }
        Object raw = data.get("messages");
        if (raw instanceof ReplaceAllWith<?> replaceAll) {
            return replaceAll.newValues();
        }
        if (raw instanceof List<?> values) {
            return values;
        }
        return List.of();
    }
}
