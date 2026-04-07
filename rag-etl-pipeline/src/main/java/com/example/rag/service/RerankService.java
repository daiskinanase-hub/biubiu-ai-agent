package com.example.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 重排服务：调用阿里云 qwen3-rerank 兼容 API 对候选文档进行语义相关性二次排序。
 * <p>
 * Rerank（重排序）用于在向量检索之后对候选文档进行更精准的相关性评分，
 * 通过将用户查询与文档内容一起发送给重排模型，计算出更准确的相关性得分。
 * </p>
 *
 * @see <a href="https://help.aliyun.com/zh/model-studio/developer-reference/use-qwen-reranker-for-text-reranking">阿里云 Rerank 文档</a>
 */
@Service
public class RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String dashScopeApiKey;
    private final String rerankModel;

    /**
     * 构造重排服务。
     *
     * @param rerankRestClient 来自 RagQueryConfig 的 RestClient 实例
     * @param objectMapper     JSON 处理工具
     * @param dashScopeApiKey  DashScope API Key
     * @param rerankModel      重排模型名称，默认 qwen3-rerank
     */
    public RerankService(RestClient rerankRestClient,
                         ObjectMapper objectMapper,
                         @Value("${spring.ai.dashscope.api-key}") String dashScopeApiKey,
                         @Value("${rag.query.rerank-model:qwen3-rerank}") String rerankModel) {
        this.restClient = rerankRestClient;
        this.objectMapper = objectMapper;
        this.dashScopeApiKey = dashScopeApiKey;
        this.rerankModel = rerankModel;
    }

    /**
     * 对候选文档进行重排。
     * <p>
     * 将用户查询和候选文档列表一起发送给重排模型，返回按相关性得分降序排列的结果。
     * </p>
     *
     * @param query     用户原始查询
     * @param documents 候选文档列表
     * @param topN      返回前 N 条结果
     * @return 按 relevanceScore 降序排列的重排结果列表
     */
    public List<RerankResult> rerank(String query, List<Document> documents, int topN) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<String> docTexts = documents.stream()
                .map(Document::getText)
                .toList();

        log.info("[Rerank] 调用重排 API | query={} | docCount={} | topN={}",
                query, documents.size(), topN);

        JsonNode requestBody = objectMapper.createObjectNode()
                .put("model", rerankModel)
                .put("query", query)
                .put("top_n", topN)
                .set("documents", objectMapper.valueToTree(docTexts));

        JsonNode response = restClient.post()
                .uri("/compatible-api/v1/reranks")
                .header("Authorization", "Bearer " + dashScopeApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        return parseResults(response, documents);
    }

    /**
     * 解析重排 API 响应，将索引映射回原始文档。
     *
     * @param response   API 响应 JSON
     * @param sourceDocs 原始文档列表（用于索引映射）
     * @return 重排结果列表
     */
    private List<RerankResult> parseResults(JsonNode response, List<Document> sourceDocs) {
        if (response == null) {
            log.warn("[Rerank] API 返回空响应");
            return List.of();
        }
        JsonNode resultsNode = response.path("output").path("results");
        if (resultsNode == null || !resultsNode.isArray() || resultsNode.isEmpty()) {
            resultsNode = response.path("results");
        }
        if (resultsNode == null || !resultsNode.isArray()) {
            log.warn("[Rerank] 响应中未找到 results 节点");
            return List.of();
        }

        List<RerankResult> results = new ArrayList<>();
        for (JsonNode node : resultsNode) {
            int index = node.path("index").asInt(-1);
            double score = node.path("relevance_score").asDouble(0.0);
            if (index >= 0 && index < sourceDocs.size()) {
                results.add(new RerankResult(sourceDocs.get(index), score));
            }
        }
        log.info("[Rerank] 解析完成 | 有效结果数={}", results.size());
        return results;
    }

    /**
     * 重排结果记录，包含原始文档和相关性得分。
     *
     * @param document        原始文档对象
     * @param relevanceScore  模型计算的相关性得分（越高越相关）
     */
    public record RerankResult(Document document, double relevanceScore) {
    }
}
