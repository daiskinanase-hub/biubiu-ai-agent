package com.example.rag.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 使用默认 ChatClient（qwen-turbo）从用户问题中提取候选标签列表。
 */
@Service
public class LabelExtractionService {

    private static final String SYSTEM_PROMPT = """
            你是企业文档分类助手。请从用户问题中提取 1-5 个最相关的文档主题标签，用于精确匹配知识库中的文档分类。
            要求：
            1. 只返回逗号分隔的标签列表；
            2. 不要添加编号、解释或任何额外文字；
            3. 标签应尽量简洁（2-8个字）。
            """;

    private final ChatClient chatClient;

    public LabelExtractionService(@Qualifier("ragChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 提取标签列表。
     *
     * @param prompt 用户原始提示词
     * @return 去重后的标签列表；提取失败则返回空列表
     */
    public List<String> extractLabels(String prompt) {
        try {
            String result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("用户问题：\n" + prompt)
                    .call()
                    .content();
            if (!StringUtils.hasText(result)) {
                return List.of();
            }
            return Arrays.stream(result.split("[,，]"))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
