package com.wuji.assistant.agent.stream;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.OverAllStateBuilder;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.state.ReplaceAllWith;
import com.wuji.assistant.agent.checkpoint.CheckpointSaverFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.function.FunctionToolCallback;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * checkpoint 输入必须整体替换 messages。走 putData，避免 withData 把 ReplaceAllWith 收成 Map。
 *
 * @author liudy
 */
class CheckpointGraphCallerTest {

    @Test
    void replaceAllInput_wrapsMessages() {
        Map<String, Object> inputs = CheckpointGraphCaller.replaceAllInput(List.of(new UserMessage("hi")), "hi");
        assertInstanceOf(ReplaceAllWith.class, inputs.get("messages"));
        assertEquals("hi", inputs.get("input"));
        ReplaceAllWith<?> replaced = (ReplaceAllWith<?>) inputs.get("messages");
        assertEquals(1, replaced.newValues().size());
    }

    @Test
    void invoke_compilesGraphBeforeCall() throws GraphRunnerException {
        ReactAgent agent = mock(ReactAgent.class);
        CompiledGraph graph = mock(CompiledGraph.class);
        OverAllState state = mock(OverAllState.class);
        when(agent.getAndCompileGraph()).thenReturn(graph);
        when(graph.getKeyStrategyMap()).thenReturn(Map.of());
        when(state.data()).thenReturn(Map.of("messages", List.of(new AssistantMessage("晴"))));
        when(graph.invoke(any(OverAllState.class), any(RunnableConfig.class))).thenReturn(Optional.of(state));

        AssistantMessage reply = CheckpointGraphCaller.invoke(
                agent,
                List.of(new UserMessage("东京的天气")),
                "东京的天气",
                RunnableConfig.builder().threadId("t1").build());

        assertEquals("晴", reply.getText());
        verify(agent).getAndCompileGraph();
        verify(agent, never()).getCompiledGraph();
    }

    @Test
    void withData_turnsReplaceAllMessagesIntoMaps() {
        Map<String, Object> inputs = CheckpointGraphCaller.replaceAllInput(List.of(new UserMessage("东京的天气")), "东京的天气");
        Object copied = OverAllStateBuilder.builder().withData(inputs).build().data().get("messages");
        ReplaceAllWith<?> replaced = assertInstanceOf(ReplaceAllWith.class, copied);
        assertFalse(replaced.newValues().get(0) instanceof Message);
        assertInstanceOf(Map.class, replaced.newValues().get(0));
    }

    @Test
    void jacksonSerializer_roundTripsMessages() throws Exception {
        Map<String, Object> data = Map.of("messages", List.of(new UserMessage("东京的天气")));
        Object cloned = StateGraph.DEFAULT_JACKSON_SERIALIZER.cloneObject(data).data().get("messages");
        assertInstanceOf(List.class, cloned);
        assertInstanceOf(UserMessage.class, ((List<?>) cloned).get(0));
        assertEquals("东京的天气", ((UserMessage) ((List<?>) cloned).get(0)).getText());
        assertSame(StateGraph.DEFAULT_JACKSON_SERIALIZER, CheckpointSaverFactory.graphSerializer());
    }

    @Test
    void initialState_keepsReplaceAllWithMessages() {
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.just(call(prompt));
            }
        };
        ReactAgent agent = ReactAgent.builder()
                .name("state-agent")
                .model(model)
                .saver(new MemorySaver())
                .stateSerializer(CheckpointSaverFactory.graphSerializer())
                .build();
        OverAllState state = CheckpointGraphCaller.initialState(
                agent.getAndCompileGraph(), List.of(new UserMessage("东京的天气")), "东京的天气");
        ReplaceAllWith<?> replaced = assertInstanceOf(ReplaceAllWith.class, state.data().get("messages"));
        assertInstanceOf(UserMessage.class, replaced.newValues().get(0));
        assertEquals("东京的天气", ((UserMessage) replaced.newValues().get(0)).getText());
    }

    @Test
    void invoke_weatherTool_survivesMapMessages() throws GraphRunnerException {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                if (calls.getAndIncrement() == 0) {
                    AssistantMessage tool = AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(new AssistantMessage.ToolCall(
                                    "call-1", "function", "get_weather", "{\"city\":\"东京\"}")))
                            .build();
                    return new ChatResponse(List.of(new Generation(tool)));
                }
                return new ChatResponse(List.of(new Generation(new AssistantMessage("东京晴"))));
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.just(call(prompt));
            }
        };
        var tool = FunctionToolCallback.builder("get_weather", (City req) -> "东京晴")
                .description("查询城市天气")
                .inputType(City.class)
                .build();
        ReactAgent agent = ReactAgent.builder()
                .name("weather-agent")
                .model(model)
                .saver(new MemorySaver())
                .stateSerializer(CheckpointSaverFactory.graphSerializer())
                .tools(tool)
                .build();
        RunnableConfig config = RunnableConfig.builder().threadId("u1:c1").build();
        AssistantMessage first = CheckpointGraphCaller.invoke(
                agent, List.of(new UserMessage("东京的天气")), "东京的天气", config);
        assertTrue(first.getText().contains("晴"));
    }

    @Test
    void stream_secondTurn_doesNotReplayPreviousAssistant() {
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                String user = "";
                for (Message message : prompt.getInstructions()) {
                    if (message instanceof UserMessage) {
                        user = message.getText();
                    }
                }
                String reply = user.contains("上海") ? "上海明天阴" : "东京今天晴";
                return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.just(call(prompt));
            }
        };
        ReactAgent agent = ReactAgent.builder()
                .name("replay-agent")
                .model(model)
                .saver(new MemorySaver())
                .stateSerializer(CheckpointSaverFactory.graphSerializer())
                .build();
        RunnableConfig config = RunnableConfig.builder().threadId("u1:replay").build();
        List<Message> first = CheckpointGraphCaller.stream(
                        agent,
                        List.of(new UserMessage("东京的天气怎么样？")),
                        "东京的天气怎么样？",
                        config)
                .collectList()
                .block(Duration.ofSeconds(30));
        assertFalse(first.isEmpty());
        String firstText = first.get(first.size() - 1).getText();
        assertTrue(firstText.contains("东京今天晴"));

        List<Message> second = CheckpointGraphCaller.stream(
                        agent,
                        List.of(
                                new UserMessage("东京的天气怎么样？"),
                                new AssistantMessage(firstText),
                                new UserMessage("那上海明天呢？")),
                        "那上海明天呢？",
                        config)
                .collectList()
                .block(Duration.ofSeconds(30));
        assertFalse(second.isEmpty());
        for (Message message : second) {
            assertFalse(firstText.equals(message.getText()), "replayed previous assistant");
            assertFalse(message.getText().startsWith(firstText));
        }
        assertTrue(second.get(second.size() - 1).getText().contains("上海明天阴"));
    }

    public record City(String city) {
    }
}
