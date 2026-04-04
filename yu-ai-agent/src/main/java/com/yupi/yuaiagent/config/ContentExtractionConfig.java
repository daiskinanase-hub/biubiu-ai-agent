package com.yupi.yuaiagent.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.yupi.yuaiagent.service.ContentExtractor;
import com.yupi.yuaiagent.service.impl.ContentExtractorImpl;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 内容提取服务配置类
 * 使用轻量级模型 qwen-turbo 进行内容提取，降低API调用成本
 */
@Configuration
@EnableConfigurationProperties(ContentExtractionConfig.ContentExtractionProperties.class)
@Slf4j
public class ContentExtractionConfig {

    @Data
    @ConfigurationProperties(prefix = "content-extraction")
    public static class ContentExtractionProperties {
        /**
         * 是否启用内容提取
         */
        private boolean enabled = true;
        
        /**
         * 内容提取使用的模型名称: qwen-turbo, qwen-plus 等
         */
        private String model = "qwen-turbo";
    }

    /**
     * 内容提取专用 ChatClient - 使用轻量级模型 qwen-turbo
     * 使用 @Qualifier 指定使用 dashscopeChatModel，因为可能同时存在 Ollama 的 ChatModel
     */
    @Bean(name = "contentExtractionChatClient")
    public ChatClient contentExtractionChatClient(
            @Qualifier("dashscopeChatModel") ChatModel chatModel,
            ContentExtractionProperties properties) {
        
        log.info("初始化内容提取 ChatClient，使用模型: {}", properties.getModel());
        
        // 创建 DashScopeChatOptions，指定使用轻量级模型
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(properties.getModel())
                .withTemperature(0.1)  // 低温度，更确定性的输出
                .build();
        
        return ChatClient.builder(chatModel)
                .defaultOptions(options)
                .defaultSystem("你是一个专门提取关键信息的助手，擅长从混乱的原始数据中提取有价值的内容。请简洁准确地提取关键信息。")
                .build();
    }

    /**
     * 主内容提取器 - 使用轻量级模型 qwen-turbo
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "content-extraction.enabled", havingValue = "true", matchIfMissing = true)
    public ContentExtractor contentExtractor(
            @Qualifier("contentExtractionChatClient") ChatClient chatClient) {
        log.info("初始化内容提取服务，使用轻量级模型");
        return new ContentExtractorImpl(chatClient);
    }

    /**
     * 备用内容提取器 - 当主提取器不可用时
     */
    @Bean
    @ConditionalOnMissingBean(ContentExtractor.class)
    public ContentExtractor fallbackContentExtractor() {
        log.warn("内容提取服务未启用或配置失败，将使用原始内容");
        return new ContentExtractor() {
            @Override
            public String extractFromSearchResult(String rawSearchResult, String query) {
                return rawSearchResult;
            }

            @Override
            public String extractFromWebPage(String rawHtml, String url) {
                // 简单清理HTML标签
                return rawHtml.replaceAll("<[^>]+>", " ").trim();
            }

            @Override
            public String extract(String rawContent, String context, String extractionType) {
                return rawContent;
            }
        };
    }
}
