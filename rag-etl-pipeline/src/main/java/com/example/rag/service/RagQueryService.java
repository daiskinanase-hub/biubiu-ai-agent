package com.example.rag.service;

import com.example.rag.dto.RagQueryRequest;
import com.example.rag.dto.RagQueryResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;

/**
 * RAG 查询主编排服务：标签提取 → 向量检索 → 重排 → 生成。
 */
@Service
public class RagQueryService {

    private static final Logger log = LoggerFactory.getLogger(RagQueryService.class);

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final LabelExtractionService labelExtractionService;
    private final RerankService rerankService;
    private final ChatClient qwen3MaxChatClient;
    private final double rerankMinScore;
    private final int vectorSearchTopK;

    public RagQueryService(JdbcTemplate jdbcTemplate,
                           EmbeddingModel embeddingModel,
                           ObjectMapper objectMapper,
                           LabelExtractionService labelExtractionService,
                           RerankService rerankService,
                           @Qualifier("qwen3MaxChatClient") ChatClient qwen3MaxChatClient,
                           @Value("${rag.query.rerank-min-score:0.3}") double rerankMinScore,
                           @Value("${rag.query.vector-search-top-k:10}") int vectorSearchTopK) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
        this.labelExtractionService = labelExtractionService;
        this.rerankService = rerankService;
        this.qwen3MaxChatClient = qwen3MaxChatClient;
        this.rerankMinScore = rerankMinScore;
        this.vectorSearchTopK = vectorSearchTopK;
    }

    /**
     * 执行完整 RAG 查询流程（非流式），返回完整回答。
     *
     * @param request 用户请求
     * @return 包含 answer 和 docCount 的响应
     */
    public RagQueryResponse query(RagQueryRequest request) {
        log.info("[RAG] 用户初始查询 | documentId={} | prompt={}", request.documentId(), request.prompt());

        String failedStage = null;
        try {
            // 1. 提取标签（用户关键词）
            failedStage = "标签提取";
            long t1 = System.currentTimeMillis();
            List<String> labels = labelExtractionService.extractLabels(request.prompt());
            long t1Cost = System.currentTimeMillis() - t1;
            log.info("[RAG] 标签提取完成 | 耗时={}ms | 提取了 {} 个关键字 | labels={}", t1Cost, labels.size(), labels);

            // 2. 向量检索（原生 SQL：document_id 过滤 + keywords 数组交集过滤 + 向量相似度排序）
            failedStage = "向量检索";
            long t2 = System.currentTimeMillis();
            List<Document> retrievedDocs = searchWithKeywords(request.documentId(), request.prompt(), labels, vectorSearchTopK);
            long t2Cost = System.currentTimeMillis() - t2;
            log.info("[RAG] 向量检索完成 | 耗时={}ms | 返回 {} 条文档", t2Cost, retrievedDocs == null ? 0 : retrievedDocs.size());
            if (retrievedDocs != null && !retrievedDocs.isEmpty()) {
                for (int i = 0; i < retrievedDocs.size(); i++) {
                    Document doc = retrievedDocs.get(i);
                    log.info("[RAG] 向量检索结果 [{}] | metadata={} | text={}",
                            i, doc.getMetadata(),
                            doc.getText() == null ? "null" : doc.getText().substring(0, Math.min(200, doc.getText().length())));
                }
            }

            // 3. 重排与阈值过滤
            failedStage = "重排";
            List<Document> contextDocs;
            if (retrievedDocs == null || retrievedDocs.isEmpty()) {
                contextDocs = List.of();
                log.info("[RAG] 向量检索结果为空，跳过重排");
            } else {
                long t3 = System.currentTimeMillis();
                List<RerankService.RerankResult> reranked = rerankService.rerank(
                        request.prompt(), retrievedDocs, 5);
                long aboveThreshold = reranked.stream()
                        .filter(r -> r.relevanceScore() >= rerankMinScore)
                        .count();
                contextDocs = reranked.stream()
                        .filter(r -> r.relevanceScore() >= rerankMinScore)
                        .map(RerankService.RerankResult::document)
                        .limit(5)
                        .toList();
                long t3Cost = System.currentTimeMillis() - t3;
                log.info("[RAG] 重排完成 | 耗时={}ms | 重排返回 {} 条 | 超过阈值(>={}) {} 条 | 最终截断后 {} 条",
                        t3Cost, reranked.size(), rerankMinScore, aboveThreshold, contextDocs.size());
                for (int i = 0; i < reranked.size(); i++) {
                    RerankService.RerankResult r = reranked.get(i);
                    log.info("[RAG] 重排结果 [{}] | score={} | text={}",
                            i, r.relevanceScore(),
                            r.document().getText() == null ? "null" : r.document().getText().substring(0, Math.min(200, r.document().getText().length())));
                }
            }

            // 4. 生成完整回答（非流式）
            failedStage = "生成";
            long t4 = System.currentTimeMillis();
            String answer = generateAnswer(request.prompt(), contextDocs);
            log.info("[RAG] 生成完成 | 耗时={}ms | contextDocs={}", System.currentTimeMillis() - t4, contextDocs.size());

            return new RagQueryResponse(answer, contextDocs.size(), null);

        } catch (Exception e) {
            log.error("[RAG] 流程异常 | 失败阶段={} | error={}", failedStage, e.getMessage(), e);
            return new RagQueryResponse(null, 0, e.getMessage());
        }
    }

    /**
     * 生成完整回答（非流式）。
     */
    private String generateAnswer(String prompt, List<Document> contextDocs) {
        String systemText = buildSystemPrompt(contextDocs);
        log.info("[Generation] 最终给大模型的 System Prompt | length={}\n{}"
                , systemText.length(), systemText);
        log.info("[Generation] 最终给大模型的 User Prompt | prompt={}", prompt);

        return qwen3MaxChatClient.prompt()
                .system(systemText)
                .user(prompt)
                .call()
                .content();
    }

    private String buildSystemPrompt(List<Document> contextDocs) {
        if (contextDocs == null || contextDocs.isEmpty()) {
            return """
                    你是企业知识库问答助手。未能在知识库中检索到与用户问题相关的资料。
                    请基于你的通用知识直接回答，并在回答开头说明"未检索到相关知识，以下回答基于模型通用知识"。
                    """;
        }
        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < contextDocs.size(); i++) {
            Document doc = contextDocs.get(i);
            ctx.append("【参考资料 ").append(i + 1).append("】\n");
            ctx.append(doc.getText()).append("\n\n");
        }
        return """
                你是企业知识库问答助手。请严格根据下面提供的参考资料回答用户问题。
                规则：
                1. 如果参考资料足以回答问题，请基于资料进行总结。
                2. 如果参考资料不足以回答问题，请明确说明。
                3. 禁止编造不在参考资料中的内容。

                参考资料：
                """ + ctx;
    }

    /**
     * 使用原生 SQL 执行向量检索，支持 keywords 数组交集过滤。
     * SQL 逻辑：document_id = ? AND (metadata->'keywords')::text[] && ?::text[]
     */
    private List<Document> searchWithKeywords(String documentId, String queryText, List<String> userKeywords, int topK) {
        // 生成查询向量
        float[] queryEmbedding = embeddingModel.embed(queryText);
        String vectorStr = vectorToString(queryEmbedding);

        // 构建 SQL
        String sql;
        Object[] params;

        if (CollectionUtils.isEmpty(userKeywords)) {
            // 无关键词：只按 document_id 过滤
            sql = """
                    SELECT id, content, metadata, embedding,
                           embedding <=> ?::vector as distance
                    FROM vector_store
                    WHERE metadata->>'document_id' = ?
                    ORDER BY embedding <=> ?::vector
                    LIMIT ?
                    """;
            params = new Object[]{vectorStr, documentId, vectorStr, topK};
        } else {
            // 有关键词：document_id + keywords JSONB 数组交集过滤
            sql = """
                    SELECT id, content, metadata, embedding,
                           embedding <=> ?::vector as distance
                    FROM vector_store
                    WHERE metadata->>'document_id' = ?
                      AND jsonb_exists_any(metadata->'keywords', ?::text[])
                    ORDER BY embedding <=> ?::vector
                    LIMIT ?
                    """;
            String keywordsArray = userKeywords.stream()
                    .map(k -> "\"" + k.replace("\"", "\\\"") + "\"")
                    .reduce((a, b) -> a + "," + b)
                    .map(s -> "{" + s + "}")
                    .orElse("{}");
            params = new Object[]{vectorStr, documentId, keywordsArray, vectorStr, topK};
        }

        log.info("[向量检索] SQL: {}", sql);
        log.info("[向量检索] 参数: documentId={}, keywordsArray={}, topK={}", documentId,
                (params.length > 2 ? params[2] : "N/A"), topK);

        String countSql = "SELECT COUNT(*) FROM vector_store WHERE metadata->>'document_id' = ?";
        Integer totalCount = jdbcTemplate.queryForObject(countSql, Integer.class, documentId);
        log.info("[向量检索] document_id='{}' 的总记录数: {}", documentId, totalCount);

        List<Document> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
            try {
                String content = rs.getString("content");
                String metadataJson = rs.getString("metadata");
                Map<String, Object> metadata = objectMapper.readValue(metadataJson, new TypeReference<>() {});
                double distance = rs.getDouble("distance");
                metadata.put("distance", distance);
                return new Document(content, metadata);
            } catch (Exception e) {
                throw new RuntimeException("解析向量检索结果失败", e);
            }
        }, params);

        log.info("[向量检索] 返回结果数: {}", results.size());
        return results;
    }

    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}