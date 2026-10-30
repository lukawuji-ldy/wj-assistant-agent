package com.wuji.assistant.agent.token;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.memory.retrieve.LongTermMemoryRetriever;
import com.wuji.assistant.memory.retrieve.MemoryItem;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 拼好系统提示和对话后，超限时只丢最旧历史轮。不裁系统、当轮问题、长期记忆、摘要和已写入的工具对。
 *
 * @author liudy
 */
@Component
public class ConversationTrimmer {

    static final String SUMMARY_PREFIX = "会话摘要（watermark 覆盖范围）:\n";

    /**
     * 只拼系统提示和当轮问题，不计数、不压缩、不丢历史。
     *
     * @param draft 稳定前缀
     * @return 尚未按预算裁剪的入模原稿
     */
    public TrimmedRequest assemble(ContextDraft draft) {
        ContextDraft source = draft == null ? ContextDraft.empty() : draft;
        String system = renderSystem(source.systemRules(), source.summary(), source.memoryItems());
        String userPrompt = text(source.userPrefix()) + text(source.userRaw()) + text(source.userSuffix());
        List<Message> messages = renderDialogue(new ArrayList<>(source.history()), userPrompt);
        return new TrimmedRequest(system, userPrompt, List.copyOf(messages), null, 0, 0, false);
    }

    /**
     * 超过输入预算时丢掉最旧历史轮，直到装下。未超则原样返回。
     *
     * @param systemPrompt     系统提示
     * @param messages         历史、当轮问题和已有工具对，不含 system
     * @param inputBudget      {@code W - R}
     * @param toolSchemaTokens 工具 schema 占用的 token
     * @param counter          该 config 的计数器
     * @return 裁剪后的请求；历史丢尽仍超则抛出 {@code BAD_REQUEST}
     */
    public TrimmedRequest trimConversation(String systemPrompt, List<Message> messages, int inputBudget,
                                           int toolSchemaTokens, TokenCounter counter) {
        return trim(systemPrompt, messages, inputBudget, toolSchemaTokens, counter, false);
    }

    /**
     * 先至少丢掉一轮最旧历史，仍超再继续丢，直到装下。用于上下文超限后的一次重试。
     *
     * @param systemPrompt     系统提示
     * @param messages         历史、当轮问题和已有工具对，不含 system
     * @param inputBudget      {@code W - R}
     * @param toolSchemaTokens 工具 schema 占用的 token
     * @param counter          该 config 的计数器
     * @return 裁剪后的请求；没有可丢的历史轮时保持原消息
     */
    public TrimmedRequest trimConversationDroppingAtLeastOne(String systemPrompt, List<Message> messages,
                                                             int inputBudget, int toolSchemaTokens,
                                                             TokenCounter counter) {
        return trim(systemPrompt, messages, inputBudget, toolSchemaTokens, counter, true);
    }

    public static String renderSystem(String systemRules, String summary, List<MemoryItem> memory) {
        StringBuilder system = new StringBuilder(text(systemRules));
        String memoryBlock = LongTermMemoryRetriever.formatMemoryItems(memory);
        if (!memoryBlock.isEmpty()) {
            appendBreak(system);
            system.append(memoryBlock);
        }
        if (!text(summary).isEmpty()) {
            appendBreak(system);
            system.append(SUMMARY_PREFIX).append(summary.trim());
        }
        return system.toString();
    }

    private TrimmedRequest trim(String systemPrompt, List<Message> messages, int inputBudget, int toolSchemaTokens,
                                TokenCounter counter, boolean dropAtLeastOne) {
        TokenCounter tokens = counter == null ? TokenCounterRegistry.forConfig(null) : counter;
        String system = text(systemPrompt);
        List<Message> kept = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
        int budget = Math.max(0, inputBudget);
        int tools = Math.max(0, toolSchemaTokens);
        int originalHistory = historyUserCount(kept);
        int originalCounted = count(system, kept, tools, tokens);
        int counted = originalCounted;
        int dropped = 0;
        if (dropAtLeastOne) {
            List<Message> next = dropOldestTurn(kept);
            if (next.size() == kept.size()) {
                return result(system, kept, originalCounted, 0, originalCounted > budget, null);
            }
            kept = next;
            dropped++;
            counted = count(system, kept, tools, tokens);
        }
        while (counted > budget) {
            List<Message> next = dropOldestTurn(kept);
            if (next.size() == kept.size()) {
                throw new WujiException(ErrorCode.BAD_REQUEST, "问题过长，缩短后再试");
            }
            kept = next;
            dropped++;
            counted = count(system, kept, tools, tokens);
        }
        int keptHistory = historyUserCount(kept);
        int droppedUsers = Math.max(dropped, originalHistory - keptHistory);
        Map<String, Object> truncation = droppedUsers > 0
                ? truncation(originalCounted, counted, droppedUsers)
                : null;
        return result(system, kept, counted, droppedUsers, false, truncation);
    }

    private static TrimmedRequest result(String system, List<Message> messages, int counted,
                                         int droppedUsers, boolean overBudget, Map<String, Object> truncation) {
        String userPrompt = lastUserText(messages);
        return new TrimmedRequest(system, userPrompt, List.copyOf(messages), truncation, counted, droppedUsers, overBudget);
    }

    static int count(String system, List<Message> messages, int toolSchemaTokens, TokenCounter tokens) {
        List<Message> all = new ArrayList<>();
        if (!text(system).isEmpty()) {
            all.add(new SystemMessage(system));
        }
        if (messages != null) {
            all.addAll(messages);
        }
        int messageTokens = all.isEmpty() ? 0 : tokens.countMessages(all);
        return messageTokens + Math.max(0, toolSchemaTokens);
    }

    /**
     * 丢掉当前问题之前最旧的一轮。最后一条 user 是当轮问题，连同其后的工具对保留。
     */
    static List<Message> dropOldestTurn(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages == null ? List.of() : messages;
        }
        int current = lastUserIndex(messages);
        if (current <= 0) {
            return messages;
        }
        int firstUser = -1;
        for (int i = 0; i < current; i++) {
            if (messages.get(i) instanceof UserMessage) {
                firstUser = i;
                break;
            }
        }
        int end;
        if (firstUser < 0) {
            end = current;
            firstUser = 0;
        } else {
            end = current;
            for (int i = firstUser + 1; i < current; i++) {
                if (messages.get(i) instanceof UserMessage) {
                    end = i;
                    break;
                }
            }
        }
        List<Message> kept = new ArrayList<>(messages.size() - (end - firstUser));
        kept.addAll(messages.subList(0, firstUser));
        kept.addAll(messages.subList(end, messages.size()));
        return kept;
    }

    private static List<Message> renderDialogue(List<Message> history, String userPrompt) {
        List<Message> messages = new ArrayList<>();
        if (history != null) {
            for (Message message : history) {
                if (message instanceof UserMessage || message instanceof AssistantMessage) {
                    messages.add(message);
                }
            }
        }
        messages.add(new UserMessage(userPrompt == null ? "" : userPrompt));
        return messages;
    }

    private static int historyUserCount(List<Message> messages) {
        int current = lastUserIndex(messages);
        int count = 0;
        for (int i = 0; i < current; i++) {
            if (messages.get(i) instanceof UserMessage) {
                count++;
            }
        }
        return count;
    }

    private static int lastUserIndex(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                return i;
            }
        }
        return -1;
    }

    private static String lastUserText(List<Message> messages) {
        int index = lastUserIndex(messages);
        if (index < 0) {
            return "";
        }
        String text = messages.get(index).getText();
        return text == null ? "" : text;
    }

    private static Map<String, Object> truncation(int originalTokens, int keptTokens, int droppedUsers) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "history");
        row.put("originalTokens", originalTokens);
        row.put("keptTokens", keptTokens);
        row.put("droppedMessages", droppedUsers);
        row.put("action", "dropped");
        row.put("droppedItems", droppedUsers);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("truncated", true);
        info.put("segments", List.of(row));
        return info;
    }

    private static void appendBreak(StringBuilder system) {
        if (!system.isEmpty()) {
            system.append("\n\n");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    /**
     * @param systemPrompt          交给系统提示拦截器的全文
     * @param userPrompt            当轮问题
     * @param messages              历史、当轮问题和已有工具对，不含 system
     * @param truncationInfo        丢掉历史时非空
     * @param estimatedPromptTokens 消息加工具 schema 的计数
     * @param droppedUserMessages   丢掉的历史 user 条数
     * @param overBudget            无法再丢历史时，计数是否仍超过输入预算
     */
    public record TrimmedRequest(
            String systemPrompt,
            String userPrompt,
            List<Message> messages,
            Map<String, Object> truncationInfo,
            int estimatedPromptTokens,
            int droppedUserMessages,
            boolean overBudget
    ) {
    }
}
