package com.example.rag.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * ChatClient 工厂配置。{@code EmbeddingModel}、{@code PgVectorStore} / {@link org.springframework.ai.vectorstore.VectorStore}
 * 由 Spring AI Alibaba 与 {@code spring-ai-pgvector-store} 自动配置，按类型注入即可。
 * <p>
 * 本配置类负责构建两个可复用的 ChatClient 实例：
 * <ul>
 *   <li>ETL 管道专用的 ChatClient（用于元数据抽取），连接超时 10s，读超时 60s</li>
 *   <li>RAG 查询专用的 ChatClient（用于生成回答），连接超时 30s，读超时 180s</li>
 * </ul>
 * </p>
 *
 * @see org.springframework.ai.chat.client.ChatClient
 */
@Configuration
public class AiConfig {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS    = 60_000;

    /**
     * 创建带超时配置的 HTTP 请求工厂。
     *
     * @return 配置好的 ClientHttpRequestFactory 实例
     */
    private ClientHttpRequestFactory chatRequestFactory() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(CONNECT_TIMEOUT_MS);
        f.setReadTimeout(READ_TIMEOUT_MS);
        return f;
    }

    /**
     * 构建用于元数据抽取等场景的 {@link ChatClient}。
     * <p>在 Spring AI 1.1.2 中，ChatClient.Builder 不再支持直接设置 requestFactory，
     * 因此通过自定义 DashScopeApi → DashScopeChatModel 的方式注入带超时的 RestClient。</p>
     *
     * @param apiKey       DashScope API Key
     * @param defaultModel 默认对话模型
     * @return 可复用的 ChatClient 实例，连接超时 10s，读超时 60s
     */
    @Bean
    public ChatClient ragChatClient(
            @Value("${spring.ai.dashscope.api-key}") String apiKey,
            @Value("${spring.ai.dashscope.chat.options.model:qwen-turbo}") String defaultModel) {

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(chatRequestFactory());

        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .build();

        DashScopeChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder().model(defaultModel).build())
                .build();

        return ChatClient.builder(chatModel).build();
    }
}
