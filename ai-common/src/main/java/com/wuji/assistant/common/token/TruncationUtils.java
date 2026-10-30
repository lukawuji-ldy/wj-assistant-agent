package com.wuji.assistant.common.token;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 头/尾截断工具，保 UTF-8 字符完整性。
 * <p>
 * 预算来自 {@link TokenEstimator} 的整段估算（含已注入的分词器），按码点二分找最长前缀或后缀，
 * 避免把英文单词拆成单字母再各自抬成 1 token。这是粗估截断，不是模型上下文硬上限。
 * <ul>
 *   <li>{@link #truncateHead(String, int)}：从头部截断，保留尾部 N token 内容</li>
 *   <li>{@link #truncateTail(String, int)}：从尾部截断，保留头部 N token 内容</li>
 *   <li>{@link #truncateLine(String, int)}：按行粒度从尾部截断，保证行完整</li>
 * </ul>
 *
 * @author liudy
 */
public final class TruncationUtils {

    private TruncationUtils() {
    }

    public record TruncationResult(
            String output,
            boolean truncated,
            int truncatedByTokens,
            int outputTokens,
            int originalTokens,
            int outputBytes
    ) {
    }

    public static TruncationResult truncateTail(String input, int maxTokens) {
        return truncate(input, maxTokens, true);
    }

    public static TruncationResult truncateHead(String input, int maxTokens) {
        return truncate(input, maxTokens, false);
    }

    public static TruncationResult truncateLine(String input, int maxTokens) {
        if (input == null || input.isEmpty()) {
            return new TruncationResult("", false, 0, 0, 0, 0);
        }
        int origTokens = TokenEstimator.estimate(input);
        if (origTokens <= maxTokens) {
            byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
            return new TruncationResult(input, false, 0, origTokens, origTokens, bytes.length);
        }
        String[] lines = input.split("\n", -1);
        List<String> kept = new ArrayList<>();
        int currentTokens = 0;
        for (String line : lines) {
            int lineTokens = TokenEstimator.estimate(line) + 1;
            if (currentTokens + lineTokens > maxTokens) {
                break;
            }
            kept.add(line);
            currentTokens += lineTokens;
        }
        String output = String.join("\n", kept);
        String safeOutput = ensureUtf8Boundary(output);
        byte[] outBytes = safeOutput.getBytes(StandardCharsets.UTF_8);
        int outTokens = TokenEstimator.estimate(safeOutput);
        return new TruncationResult(
                safeOutput,
                true,
                origTokens - outTokens,
                outTokens,
                origTokens,
                outBytes.length
        );
    }

    private static TruncationResult truncate(String input, int maxTokens, boolean tail) {
        if (input == null || input.isEmpty()) {
            return new TruncationResult("", false, 0, 0, 0, 0);
        }
        int origTokens = TokenEstimator.estimate(input);
        if (origTokens <= maxTokens) {
            byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
            return new TruncationResult(input, false, 0, origTokens, origTokens, bytes.length);
        }
        int[] cp = input.codePoints().toArray();
        int start;
        int end;
        if (tail) {
            start = 0;
            end = longestWithin(cp, maxTokens, true);
        } else {
            int keep = longestWithin(cp, maxTokens, false);
            start = cp.length - keep;
            end = cp.length;
        }
        String sub = new String(cp, start, end - start);
        String out = ensureUtf8Boundary(sub);
        int outTokens = TokenEstimator.estimate(out);
        byte[] outBytes = out.getBytes(StandardCharsets.UTF_8);
        return new TruncationResult(out, true, origTokens - outTokens, outTokens, origTokens, outBytes.length);
    }

    /**
     * 启发式估算对加长文本不减，故二分得到预算内最长前缀或后缀。
     * 已注入的分词器若出现「加长后 token 变少」，这里得到的是一个不超预算的切点。
     *
     * @param prefix true 取前缀码点数，false 取后缀码点数
     */
    private static int longestWithin(int[] cp, int maxTokens, boolean prefix) {
        if (maxTokens <= 0 || cp.length == 0) {
            return 0;
        }
        int lo = 0;
        int hi = cp.length;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            String slice = prefix
                    ? new String(cp, 0, mid)
                    : new String(cp, cp.length - mid, mid);
            if (TokenEstimator.estimate(slice) <= maxTokens) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    static String ensureUtf8Boundary(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        int i = bytes.length - 1;
        int continuation = 0;
        while (i >= 0 && continuation < 3 && (bytes[i] & 0xC0) == 0x80) {
            continuation++;
            i--;
        }
        if (i >= 0 && (bytes[i] & 0xC0) == 0xC0) {
            int expected = utf8SequenceLength(bytes[i]);
            if (expected < 0 || continuation + 1 < expected) {
                return new String(bytes, 0, i, StandardCharsets.UTF_8);
            }
        }
        return s;
    }

    private static int utf8SequenceLength(byte lead) {
        if ((lead & 0xE0) == 0xC0) {
            return 2;
        }
        if ((lead & 0xF0) == 0xE0) {
            return 3;
        }
        if ((lead & 0xF8) == 0xF0) {
            return 4;
        }
        return -1;
    }
}
