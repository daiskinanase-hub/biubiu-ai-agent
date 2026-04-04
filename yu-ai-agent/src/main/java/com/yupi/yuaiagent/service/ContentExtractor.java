package com.yupi.yuaiagent.service;

/**
 * 内容提取服务接口
 * 用于调用小模型从原始内容中提取关键信息
 */
public interface ContentExtractor {

    /**
     * 从搜索结果中提取关键信息
     *
     * @param rawSearchResult 原始搜索结果（JSON格式）
     * @param query 搜索查询词
     * @return 提取后的关键信息
     */
    String extractFromSearchResult(String rawSearchResult, String query);

    /**
     * 从网页HTML中提取主要内容
     *
     * @param rawHtml 原始网页HTML
     * @param url 网页URL
     * @return 提取后的文本内容
     */
    String extractFromWebPage(String rawHtml, String url);

    /**
     * 通用内容提取
     *
     * @param rawContent 原始内容
     * @param context 上下文信息（如"搜索关键词"、"网页URL"等）
     * @param extractionType 提取类型（SEARCH_RESULT, WEB_PAGE 等）
     * @return 提取后的内容
     */
    String extract(String rawContent, String context, String extractionType);
}
