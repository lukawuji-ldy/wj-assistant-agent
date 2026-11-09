package com.wuji.assistant.agent.token;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.wuji.assistant.agent.config.WujiMemoryProperties;
import com.wuji.assistant.agent.memory.LlmSummaryOrchestrator;
import com.wuji.assistant.agent.model.LlmClientFactory;
import com.wuji.assistant.agent.model.LlmConfigRecord;
import com.wuji.assistant.agent.prompt.WujiSystemPromptInterceptor;
import com.wuji.assistant.memory.ShortTermContext;
import com.wuji.assistant.memory.ShortTermMemoryService;
import com.wuji.assistant.memory.model.ChatMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 父 Agent 和子 Agent 每次调用模型前的预算执行。超限先压缩历史，仍超再丢最旧轮。
 *
 * @author liudy
 */
@Component
public class TokenBudgetEnforcer {

    public static final String USER_ID = "wuji.userId";
    public static final String CONVERSATION_ID = "wuji.conversationId";
    public static final String USER_MESSAGE_ID = "wuji.userMessageId";
    public static final String ASSISTANT_MESSAGE_ID = "wuji.assistantMessageId";

    private final InputBudgetCalculator inputBudgetCalculator;
    private final ConversationTrimmer trimmer;
    private final LlmClientFactory llmClientFactory;
    private final LlmSummaryOrchestrator summaryOrchestrator;
    private final ShortTermMemoryService shortTermMemoryService;
    private final WujiMemoryProperties memoryProperties;
    private final Scheduler llmCallScheduler;

    public TokenBudgetEnforcer(InputBudgetCalculator inputBudgetCalculator,
                               ConversationTrimmer trimmer,
                               LlmClientFactory llmClientFactory,
                               LlmSummaryOrchestrator summaryOrchestrator,
                               ShortTermMemoryService shortTermMemoryService,
                               WujiMemoryProperties memoryProperties,
                               @Qualifier("llmCallScheduler") Scheduler llmCallScheduler) {
        this.inputBudgetCalculator = inputBudgetCalculator;
        this.trimmer = trimmer;
        this.llmClientFactory = llmClientFactory;
        this.summaryOrchestrator = summaryOrchestrator;
        this.shortTermMemoryService = shortTermMemoryService;
        this.memoryProperties = memoryProperties;
        this.llmCallScheduler = llmCallScheduler;
    }

    /**
     * 按输入预算裁剪本次请求。不调用模型。
     *
     * @param configId         本次 CHAT 行
     * @param toolSchemaTokens 工具 schema；账本里已有更大值时以账本为准
     * @param request          拦截器收到的模型请求
     * @return 写回请求用的系统提示、消息和输出上限
     */
    public EnforcedRequest enforce(String configId, int toolSchemaTokens, ModelRequest request) {
        Map<String, Object> context = request.getContext();
        // 1. 读取本次调用预算。没有账本时用配置行现算输入预算。
        CallBudget callBudget = callBudget(context);
        int tools = callBudget != null && callBudget.toolSchemaTokens() > 0
                ? callBudget.toolSchemaTokens() : Math.max(0, toolSchemaTokens);
        int inputBudget = callBudget != null
                ? callBudget.inputBudget()
                : inputBudgetCalculator.calculate(requireConfig(configId)).inputBudget();
        int outputCap = callBudget == null ? 0 : callBudget.outputCap();
        TokenCounter counter = TokenCounterRegistry.forConfig(configId);
        String system = systemText(request);
        List<Message> messages = stripSystem(request.getMessages());
        // 2. 计数系统提示、消息和工具 schema。
        int counted = ConversationTrimmer.count(system, messages, tools, counter);
        // 3. 超过输入预算时压缩最旧历史。本轮只提交一次；同步则重载，异步只提交、本轮不等。
        if (counted > inputBudget) {
            CompressedHistory compressed = compressOldestHistory(context, messages);
            if (compressed != null) {
                system = replaceSummary(system, compressed.summary());
                messages = compressed.messages();
                updateSystemPrompt(context, system);
            }
        }
        // 4. 仍超就丢最旧历史轮，直到装下。历史丢尽由 trimConversation 抛出 BAD_REQUEST。
        ConversationTrimmer.TrimmedRequest trimmed = trimmer.trimConversation(
                system, messages, inputBudget, tools, counter);
        // 5. 写回消息计数、第一次入模窗口和压缩后的系统提示。输出上限由拦截器写入 max_tokens。
        if (callBudget != null) {
            callBudget.setMessageTokens(Math.max(0, trimmed.estimatedPromptTokens() - tools));
        }
        recordFirstWindow(context, trimmed);
        updateSystemPrompt(context, trimmed.systemPrompt());
        return new EnforcedRequest(trimmed.systemPrompt(), trimmed.messages(), outputCap);
    }

    private LlmConfigRecord requireConfig(String configId) {
        return llmClientFactory.getConfig(configId);
    }

    private CompressedHistory compressOldestHistory(Map<String, Object> context, List<Message> messages) {
        if (context == null || summaryOrchestrator == null) {
            return null;
        }
        String userId = text(context.get(USER_ID));
        String conversationId = text(context.get(CONVERSATION_ID));
        // 缺用户或会话，本轮不重载。
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(conversationId)) {
            return null;
        }
        CompressOnce once = compressOnce(context);
        // 本轮已提交过压缩，不再提交。
        if (once != null && !once.tryStart()) {
            return null;
        }
        WujiMemoryProperties.ShortTerm shortTerm = memoryProperties == null ? null : memoryProperties.getShort();
        boolean sync = shortTerm != null && shortTerm.isSummarySyncEnabled();
        if (!sync) {
            // 异步模式：只提交，本轮不等。
            summaryOrchestrator.submitUrgent(llmCallScheduler, userId, conversationId);
            return null;
        }
        Duration timeout = shortTerm.getSummarySyncTimeout();
        boolean ran = summaryOrchestrator.compressUrgent(userId, conversationId, timeout);
        if (!ran) {
            return null;
        }
        int maxMessages = shortTerm.windowSize();
        ShortTermContext loaded = shortTermMemoryService.loadContext(userId, conversationId, maxMessages);
        String summary = loaded == null ? "" : loaded.summaryJson();
        List<Message> history = toDialogue(loaded == null ? List.of() : loaded.messages(),
                text(context.get(USER_MESSAGE_ID)), text(context.get(ASSISTANT_MESSAGE_ID)));
        return new CompressedHistory(summary, join(history, tailAfterCurrentUser(messages)));
    }

    private static List<Message> tailAfterCurrentUser(List<Message> messages) {
        int current = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                current = i;
                break;
            }
        }
        if (current < 0) {
            return List.of();
        }
        return new ArrayList<>(messages.subList(current, messages.size()));
    }

    private static List<Message> join(List<Message> history, List<Message> tail) {
        List<Message> messages = new ArrayList<>();
        if (history != null) {
            messages.addAll(history);
        }
        if (tail != null) {
            messages.addAll(tail);
        }
        return messages;
    }

    private static List<Message> toDialogue(List<ChatMessage> rows, String userMessageId, String assistantMessageId) {
        List<Message> messages = new ArrayList<>();
        if (rows == null) {
            return messages;
        }
        for (ChatMessage row : rows) {
            if (row == null || row.getRole() == null || row.getMessageId() == null) {
                continue;
            }
            if (row.getMessageId().equals(userMessageId) || row.getMessageId().equals(assistantMessageId)) {
                continue;
            }
            String content = row.getContent() == null ? "" : row.getContent();
            if ("user".equalsIgnoreCase(row.getRole())) {
                messages.add(new UserMessage(content));
            } else if ("assistant".equalsIgnoreCase(row.getRole())) {
                messages.add(new AssistantMessage(content));
            }
        }
        return messages;
    }

    static String replaceSummary(String system, String summary) {
        String base = system == null ? "" : system;
        String marker = ConversationTrimmer.SUMMARY_PREFIX;
        int at = base.indexOf(marker);
        String body = summary == null ? "" : summary.trim();
        if (body.isEmpty()) {
            if (at < 0) {
                return base;
            }
            return base.substring(0, at).stripTrailing();
        }
        if (at < 0) {
            return base.isEmpty() ? marker + body : base + "\n\n" + marker + body;
        }
        return base.substring(0, at) + marker + body;
    }

    private static void recordFirstWindow(Map<String, Object> context, ConversationTrimmer.TrimmedRequest trimmed) {
        if (context == null) {
            return;
        }
        Object recorder = context.get(FirstModelWindow.META_KEY);
        if (recorder instanceof FirstModelWindow window) {
            window.accept(trimmed);
        }
    }

    private static void updateSystemPrompt(Map<String, Object> context, String system) {
        if (context == null) {
            return;
        }
        Object holder = context.get(WujiSystemPromptInterceptor.META_SYSTEM_PROMPT);
        if (holder instanceof SystemPromptHolder prompt) {
            prompt.set(system);
        }
    }

    private static String systemText(ModelRequest request) {
        if (request.getContext() != null) {
            Object holder = request.getContext().get(WujiSystemPromptInterceptor.META_SYSTEM_PROMPT);
            if (holder instanceof SystemPromptHolder prompt && StringUtils.hasText(prompt.get())) {
                return prompt.get();
            }
            if (holder instanceof String text && StringUtils.hasText(text)) {
                return text;
            }
        }
        if (request.getSystemMessage() != null && request.getSystemMessage().getText() != null) {
            return request.getSystemMessage().getText();
        }
        return "";
    }

    private static List<Message> stripSystem(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        List<Message> out = new ArrayList<>(messages.size());
        for (Message message : messages) {
            if (!(message instanceof SystemMessage)) {
                out.add(message);
            }
        }
        return out;
    }

    private static CallBudget  callBudget(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object value = context.get(CallBudget.META_KEY);
        return value instanceof CallBudget found ? found : null;
    }

    private static CompressOnce compressOnce(Map<String, Object> context) {
        Object value = context.get(CompressOnce.META_KEY);
        return value instanceof CompressOnce found ? found : null;
    }

    private static String text(Object value) {
        return value instanceof String s ? s : "";
    }

    /**
     * 写回 {@link com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest} 的裁剪结果。
     *
     * @param systemPrompt 系统提示
     * @param messages     不含 system 的消息
     * @param outputCap    大于 0 时收紧本次 {@code max_tokens}
     */
    public record EnforcedRequest(String systemPrompt, List<Message> messages, int outputCap) {
    }

    private record CompressedHistory(String summary, List<Message> messages) {
    }
}
