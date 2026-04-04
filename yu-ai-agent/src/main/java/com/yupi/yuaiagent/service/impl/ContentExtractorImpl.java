package com.yupi.yuaiagent.service.impl;

import com.yupi.yuaiagent.service.ContentExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 内容提取服务实现类
 * 使用轻量级小模型提取关键信息
 */
@Service
@Slf4j
public class ContentExtractorImpl implements ContentExtractor {

    // 使用轻量级模型进行内容提取的 ChatClient
    private final ChatClient chatClient;

    // 搜索结果提取提示词
    private static final String SEARCH_EXTRACTION_PROMPT = """
        你是一个信息提取助手。请从以下搜索结果中提取与用户查询最相关的关键信息。
        
        用户查询：%s
        
        原始搜索结果：
        %s
        
        请提取以下信息并以简洁的格式返回：
        1. 搜索结果中与查询最相关的核心内容
        2. 关键数据、事实或结论
        3. 如果有时间、地点、数值等具体信息，请保留
        
        要求：
        - 只返回提取后的文本内容，不要包含JSON格式、HTML标签等原始格式
        - 保持信息的准确性和完整性
        - 去除无关的广告、导航等噪声信息
        - 如果结果不相关，说明"未找到相关信息"
        """;

    // 网页内容提取提示词
    private static final String WEBPAGE_EXTRACTION_PROMPT = """
        你是一个网页内容提取助手。请从以下网页HTML中提取主要文本内容。
        
        网页URL：%s
        
        原始HTML内容：
        %s
        
        请提取以下信息：
        1. 网页标题（如果有）
        2. 主要正文内容
        3. 关键信息、数据、结论
        
        要求：
        - 只返回提取后的纯文本内容
        - 去除所有HTML标签、CSS样式、JavaScript代码
        - 去除导航栏、广告、页脚等无关内容
        - 保留段落结构，适当换行
        - 如果内容过长，提取前3000个字符的核心内容
        """;

    // 通用提取提示词
    private static final String GENERAL_EXTRACTION_PROMPT = """
        请从以下内容中提取关键信息：
        
        上下文：%s
        
        原始内容：
        %s
        
        提取类型：%s
        
        要求：
        - 只保留核心信息，去除格式标记
        - 返回简洁的文本内容
        - 保持信息准确性
        """;

    public ContentExtractorImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String extractFromSearchResult(String rawSearchResult, String query) {
        if (rawSearchResult == null || rawSearchResult.isEmpty()) {
            return "搜索结果为空";
        }

        try {
            // 如果内容较短，直接返回
            if (rawSearchResult.length() < 500) {
                return rawSearchResult;
            }

            String prompt = String.format(SEARCH_EXTRACTION_PROMPT, query, 
                    truncate(rawSearchResult, 4000));
            
            return callModel(prompt);
        } catch (Exception e) {
            log.error("提取搜索结果时出错：{}，返回原始内容", e.getMessage());
            // 出错时返回截断的原始内容
            return truncate(rawSearchResult, 2000);
        }
    }

    @Override
    public String extractFromWebPage(String rawHtml, String url) {
        if (rawHtml == null || rawHtml.isEmpty()) {
            return "网页内容为空";
        }

        try {
            // 如果内容较短，直接返回
            if (rawHtml.length() < 1000) {
                return cleanHtml(rawHtml);
            }

            String prompt = String.format(WEBPAGE_EXTRACTION_PROMPT, url, 
                    truncate(rawHtml, 5000));
            
            return callModel(prompt);
        } catch (Exception e) {
            log.error("提取网页内容时出错：{}，返回清理后的原始内容", e.getMessage());
            // 出错时返回简单清理后的内容
            return cleanHtml(truncate(rawHtml, 2000));
        }
    }

    @Override
    public String extract(String rawContent, String context, String extractionType) {
        if (rawContent == null || rawContent.isEmpty()) {
            return "内容为空";
        }

        try {
            String prompt = String.format(GENERAL_EXTRACTION_PROMPT, 
                    context, truncate(rawContent, 4000), extractionType);
            
            return callModel(prompt);
        } catch (Exception e) {
            log.error("提取内容时出错：{}，返回原始内容", e.getMessage());
            return truncate(rawContent, 2000);
        }
    }

    /**
     * 调用模型进行内容提取
     */
    private String callModel(String prompt) {
        try {
            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            log.debug("内容提取完成，输入长度：{}，输出长度：{}", 
                    prompt.length(), result != null ? result.length() : 0);
            
            return result != null ? result.trim() : "";
        } catch (Exception e) {
            log.error("调用模型提取内容时出错：{}", e.getMessage());
            throw e;
        }
    }

    /**
     * 截断长文本
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "... [内容已截断]";
    }

    /**
     * 简单清理HTML标签（作为备用方案）
     */
    private String cleanHtml(String html) {
        if (html == null) {
            return "";
        }
        // 移除 script 和 style 标签及其内容
        String text = html.replaceAll("(?s)<script.*?>.*?</script>", "");
        text = text.replaceAll("(?s)<style.*?>.*?</style>", "");
        // 移除所有HTML标签
        text = text.replaceAll("<[^>]+>", " ");
        // 解码HTML实体
        text = text.replace("&nbsp;", " ")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&amp;", "&")
                   .replace("&quot;", "\"");
        // 压缩空白字符
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }
}
