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
 * RAG 查询链路专属配置：qwen3-max ChatClient、重排 RestClient。
 */
@Configuration
public class RagQueryConfig {

    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS   = 180_000;

    private ClientHttpRequestFactory chatRequestFactory() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(CONNECT_TIMEOUT_MS);
        f.setReadTimeout(READ_TIMEOUT_MS);
        return f;
    }

    /**
     * 用于最终流式生成的 qwen3-max ChatClient。
     * <p>在 Spring AI 1.1.2 中，ChatClient.Builder 不再支持直接设置 restClient，
     * 因此通过自定义 DashScopeApi → DashScopeChatModel 的方式注入带超时的 RestClient，
     * 防止 LLM 推理耗时较长时连接被断开。</p>
     *
     * @param apiKey DashScope API Key
     * @return 绑定 qwen3-max 默认选项的 ChatClient
     */
    @Bean(name = "qwen3MaxChatClient")
    public ChatClient qwen3MaxChatClient(
            @Value("${spring.ai.dashscope.api-key}") String apiKey) {

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(chatRequestFactory());

        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .build();

        DashScopeChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder().model("qwen3-max").build())
                .build();

        return ChatClient.builder(chatModel).build();
    }

    /**
     * 用于调用 qwen3-rerank 兼容 API 的 RestClient。
     *
     * @return RestClient 实例
     */
    @Bean(name = "rerankRestClient")
    public RestClient rerankRestClient() {
        return RestClient.builder()
                .baseUrl("https://dashscope.aliyuncs.com")
                .requestFactory(chatRequestFactory())
                .build();
    }
}
