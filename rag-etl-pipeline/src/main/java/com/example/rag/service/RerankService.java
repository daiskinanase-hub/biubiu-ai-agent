package com.example.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 调用阿里云 qwen3-rerank 兼容 API 对候选文档进行二次排序。
 */
@Service
public class RerankService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String dashScopeApiKey;
    private final String rerankModel;

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
     *
     * @param query     用户原始查询
     * @param documents 候选文档（最多 10 条）
     * @param topN      返回前 N 条
     * @return 按 relevance_score 降序排列的结果列表
     */
    public List<RerankResult> rerank(String query, List<Document> documents, int topN) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<String> docTexts = documents.stream()
                .map(Document::getText)
                .toList();

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

    private List<RerankResult> parseResults(JsonNode response, List<Document> sourceDocs) {
        if (response == null) {
            return List.of();
        }
        JsonNode resultsNode = response.path("output").path("results");
        if (resultsNode == null || !resultsNode.isArray() || resultsNode.isEmpty()) {
            resultsNode = response.path("results");
        }
        if (resultsNode == null || !resultsNode.isArray()) {
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
        return results;
    }

    /**
     * 重排结果项。
     *
     * @param document        原始文档
     * @param relevanceScore 相关性得分
     */
    public record RerankResult(Document document, double relevanceScore) {
    }
}
