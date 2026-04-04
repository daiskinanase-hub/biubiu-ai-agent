package com.example.rag.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 Markdown 标题（#、##）与段落的语义分块，
 * 结合短块合并（方案1）与超长块拆分（方案2）的混合策略。
 */
@Service
public class SemanticChunkingService {

    private static final Pattern HEADING_START = Pattern.compile("(?m)^#{1,2}\\s+.+$");
    private static final Pattern PARA_SPLIT = Pattern.compile("\\n{2,}");
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[。！？.!?])");

    private static final int MIN_CHUNK_CHARS = 300;
    private static final int MAX_CHUNK_CHARS = 1000;
    private static final int OVERLAP_CHARS = 150;

    /**
     * 将 Markdown 正文切分为语义块，采用混合策略：
     * 1. 初步切分（标题/段落）
     * 2. 短块合并（<300字合并）
     * 3. 超长块拆分（>1000字按句子/窗口拆分）
     * 4. 施加上下文重叠（150字或20%，取较小值）
     *
     * @param markdown        清洗后的 Markdown
     * @param overlapRatio    重叠比例（现在仅用于计算，实际使用固定150字或20%）
     * @return 切片列表（非空字符串）
     */
    public List<String> chunkWithOverlap(String markdown, double overlapRatio) {
        if (!StringUtils.hasText(markdown)) {
            return List.of();
        }
        String normalized = markdown.replace("\r\n", "\n").trim();

        // 步骤1：初步切分（标题优先，退化到段落）
        List<String> blocks = splitByMarkdownHeadings(normalized);
        if (blocks.size() <= 1) {
            blocks = splitByDoubleNewline(normalized);
        }

        // 过滤噪声并合并短块
        List<String> meaningful = blocks.stream()
                .map(String::trim)
                .filter(s -> s.length() >= 20)
                .toList();
        if (meaningful.isEmpty()) {
            return List.of(normalized);
        }

        // 步骤2：短块合并（方案1）
        List<String> merged = mergeShortBlocks(meaningful, MIN_CHUNK_CHARS);

        // 步骤3：处理超长块（方案2）
        List<String> sized = new ArrayList<>();
        for (String block : merged) {
            if (block.length() > MAX_CHUNK_CHARS) {
                sized.addAll(splitLongBlock(block, MAX_CHUNK_CHARS));
            } else {
                sized.add(block);
            }
        }

        // 步骤4：施加上下文重叠
        return applyOverlap(sized, overlapRatio);
    }

    /**
     * 便捷重载：固定 10% 重叠。
     *
     * @param markdown 文本
     * @return 切片
     */
    public List<String> chunkWithTenPercentOverlap(String markdown) {
        return chunkWithOverlap(markdown, 0.10d);
    }

    /**
     * 短块合并：将相邻的长度不足 minLength 的块合并，直到达到目标长度。
     *
     * @param blocks    原始块列表
     * @param minLength 最小目标长度（如300）
     * @return 合并后的块列表
     */
    private List<String> mergeShortBlocks(List<String> blocks, int minLength) {
        List<String> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String block : blocks) {
            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }
            buffer.append(block);
            // 达到最小长度或接近最大长度时，作为一个 chunk 输出
            if (buffer.length() >= minLength) {
                result.add(buffer.toString());
                buffer.setLength(0);
            }
        }
        // 处理尾部残留
        if (buffer.length() > 0) {
            if (!result.isEmpty() && buffer.length() < minLength / 2) {
                // 如果最后一个块很短（<150），拼入前一个块
                int lastIdx = result.size() - 1;
                String last = result.get(lastIdx);
                result.set(lastIdx, last + "\n\n" + buffer);
            } else {
                result.add(buffer.toString());
            }
        }
        return result;
    }

    /**
     * 超长块拆分：按句子边界拆分，必要时使用滑动窗口。
     *
     * @param block     超长文本块
     * @param maxLength 最大长度（如1000）
     * @return 拆分后的块列表
     */
    private List<String> splitLongBlock(String block, int maxLength) {
        List<String> result = new ArrayList<>();
        // 先尝试按句子拆分
        String[] sentences = SENTENCE_SPLIT.split(block);
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;
            if (current.length() + sentence.length() > maxLength && current.length() > 0) {
                result.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(" ");
            }
            current.append(sentence);
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }

        // 如果按句子拆分后仍有超长（比如单句就>1000字），用滑动窗口强制拆分
        List<String> finalResult = new ArrayList<>();
        for (String chunk : result) {
            if (chunk.length() > maxLength) {
                finalResult.addAll(slidingWindowSplit(chunk, maxLength));
            } else {
                finalResult.add(chunk);
            }
        }
        return finalResult;
    }

    /**
     * 滑动窗口拆分：固定长度+固定步长，保证不丢失内容。
     */
    private List<String> slidingWindowSplit(String text, int windowSize) {
        List<String> result = new ArrayList<>();
        int step = windowSize / 2; // 50% 重叠
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(start + windowSize, text.length());
            result.add(text.substring(start, end));
            if (end == text.length()) break;
        }
        return result;
    }

    /**
     * 使用标题行作为边界拆分；保留标题与其后正文在同一单元。
     *
     * @param text 全文
     * @return 单元列表
     */
    private List<String> splitByMarkdownHeadings(String text) {
        Matcher m = HEADING_START.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
        }
        if (starts.isEmpty()) {
            return List.of(text);
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = (i + 1) < starts.size() ? starts.get(i + 1) : text.length();
            out.add(text.substring(from, to).trim());
        }
        // 处理标题前的前置内容（如摘要）
        int first = starts.getFirst();
        if (first > 0) {
            String pre = text.substring(0, first).trim();
            if (StringUtils.hasText(pre)) {
                out.addFirst(pre);
            }
        }
        return out;
    }

    /**
     * 按双换行分段。
     *
     * @param text 全文
     * @return 段落列表
     */
    private List<String> splitByDoubleNewline(String text) {
        String[] parts = PARA_SPLIT.split(text);
        List<String> list = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (StringUtils.hasText(t)) {
                list.add(t);
            }
        }
        return list.isEmpty() ? List.of(text) : list;
    }

    /**
     * 对连续块施加上下文重叠：取 150 字符或 20%（取较小值）作为尾部前缀到下一头部。
     *
     * @param chunks        原始有序块
     * @param overlapRatio  重叠比例 ∈ (0,1)，与新逻辑并存
     * @return 施加重叠后的块列表
     */
    private List<String> applyOverlap(List<String> chunks, double overlapRatio) {
        if (chunks.size() == 1) {
            return chunks;
        }
        List<String> result = new ArrayList<>(chunks.size());
        String previous = chunks.getFirst();
        result.add(previous);
        for (int i = 1; i < chunks.size(); i++) {
            String current = chunks.get(i);
            // 计算重叠长度：150 字或 20%，取较小值
            int ratioTake = (int) Math.floor(previous.length() * overlapRatio);
            int take = Math.min(OVERLAP_CHARS, ratioTake);
            take = Math.min(take, previous.length());
            String tail = take > 0 ? previous.substring(previous.length() - take).trim() : "";
            String merged = tail.isEmpty() ? current : (tail + "\n\n" + current);
            result.add(merged.trim());
            previous = current;
        }
        return result;
    }
}
