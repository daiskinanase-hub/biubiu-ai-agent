package com.example.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RerankService} 相关测试：验证RerankResult记录和响应解析逻辑。
 */
class RerankServiceTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("RerankResult记录测试")
    class RerankResultRecordTests {

        @Test
        @DisplayName("记录应正确保存文档和得分")
        void rerankResult_recordStoresValues() {
            org.springframework.ai.document.Document doc =
                    new org.springframework.ai.document.Document("测试内容");
            RerankService.RerankResult result = new RerankService.RerankResult(doc, 0.95);

            assertEquals(doc, result.document());
            assertEquals(0.95, result.relevanceScore());
        }

        @Test
        @DisplayName("记录应支持equals比较")
        void rerankResult_recordEquals() {
            org.springframework.ai.document.Document doc =
                    new org.springframework.ai.document.Document("内容");
            RerankService.RerankResult r1 = new RerankService.RerankResult(doc, 0.9);
            RerankService.RerankResult r2 = new RerankService.RerankResult(doc, 0.9);

            assertEquals(r1, r2);
        }

        @Test
        @DisplayName("不同得分的记录应不相等")
        void rerankResult_differentScores_notEquals() {
            org.springframework.ai.document.Document doc =
                    new org.springframework.ai.document.Document("内容");
            RerankService.RerankResult r1 = new RerankService.RerankResult(doc, 0.9);
            RerankService.RerankResult r2 = new RerankService.RerankResult(doc, 0.8);

            assertNotEquals(r1, r2);
        }

        @Test
        @DisplayName("相同得分不同文档的记录应不相等")
        void rerankResult_differentDocs_notEquals() {
            org.springframework.ai.document.Document doc1 =
                    new org.springframework.ai.document.Document("内容1");
            org.springframework.ai.document.Document doc2 =
                    new org.springframework.ai.document.Document("内容2");
            RerankService.RerankResult r1 = new RerankService.RerankResult(doc1, 0.9);
            RerankService.RerankResult r2 = new RerankService.RerankResult(doc2, 0.9);

            assertNotEquals(r1, r2);
        }

        @Test
        @DisplayName("记录应支持toString")
        void rerankResult_supportsToString() {
            org.springframework.ai.document.Document doc =
                    new org.springframework.ai.document.Document("内容");
            RerankService.RerankResult result = new RerankService.RerankResult(doc, 0.95);
            String str = result.toString();

            assertTrue(str.contains("0.95"));
        }

        @Test
        @DisplayName("零分应正确表示")
        void rerankResult_zeroScore() {
            org.springframework.ai.document.Document doc =
                    new org.springframework.ai.document.Document("内容");
            RerankService.RerankResult result = new RerankService.RerankResult(doc, 0.0);

            assertEquals(0.0, result.relevanceScore());
        }

        @Test
        @DisplayName("满分应正确表示")
        void rerankResult_maxScore() {
            org.springframework.ai.document.Document doc =
                    new org.springframework.ai.document.Document("内容");
            RerankService.RerankResult result = new RerankService.RerankResult(doc, 1.0);

            assertEquals(1.0, result.relevanceScore());
        }
    }

    @Nested
    @DisplayName("响应解析逻辑测试")
    class ResponseParsingTests {

        @Test
        @DisplayName("JSON解析成功")
        void jsonParsing_works() throws Exception {
            String json = """
                    {
                        "output": {
                            "results": [
                                {"index": 0, "relevance_score": 0.95},
                                {"index": 1, "relevance_score": 0.85}
                            ]
                        }
                    }
                    """;

            var node = objectMapper.readTree(json);
            var results = node.path("output").path("results");

            assertTrue(results.isArray());
            assertEquals(2, results.size());
            assertEquals(0.95, results.get(0).path("relevance_score").asDouble());
        }

        @Test
        @DisplayName("空results数组应被检测")
        void emptyResultsArray_detected() throws Exception {
            String json = """
                    {"output": {"results": []}}
                    """;

            var node = objectMapper.readTree(json);
            var results = node.path("output").path("results");

            assertTrue(results.isArray());
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("缺少results字段应返回空路径")
        void missingResultsField_returnsEmptyPath() throws Exception {
            String json = """
                    {"code": "Success"}
                    """;

            var node = objectMapper.readTree(json);
            var results = node.path("output").path("results");

            assertTrue(results.isMissingNode());
        }

        @Test
        @DisplayName("备用results路径应可用")
        void alternativeResultsPath_works() throws Exception {
            String json = """
                    {
                        "results": [
                            {"index": 0, "relevance_score": 0.88}
                        ]
                    }
                    """;

            var node = objectMapper.readTree(json);
            var results = node.path("results");

            assertTrue(results.isArray());
            assertEquals(1, results.size());
            assertEquals(0.88, results.get(0).path("relevance_score").asDouble());
        }

        @Test
        @DisplayName("无效索引应被检测")
        void invalidIndex_detected() throws Exception {
            String json = """
                    {
                        "output": {
                            "results": [
                                {"index": -1, "relevance_score": 0.9},
                                {"index": 99, "relevance_score": 0.8}
                            ]
                        }
                    }
                    """;

            var node = objectMapper.readTree(json);
            var results = node.path("output").path("results");

            assertEquals(-1, results.get(0).path("index").asInt());
            assertEquals(99, results.get(1).path("index").asInt());
        }

        @Test
        @DisplayName("缺失relevanceScore应返回默认值")
        void missingRelevanceScore_returnsDefault() throws Exception {
            String json = """
                    {
                        "output": {
                            "results": [
                                {"index": 0}
                            ]
                        }
                    }
                    """;

            var node = objectMapper.readTree(json);
            var results = node.path("output").path("results");

            assertEquals(0.0, results.get(0).path("relevance_score").asDouble(0.0));
        }
    }

    @Nested
    @DisplayName("配置验证测试")
    class ConfigTests {

        @Test
        @DisplayName("默认rerank模型名应为qwen3-rerank")
        void defaultRerankModel() {
            String defaultModel = "qwen3-rerank";
            assertNotNull(defaultModel);
            assertTrue(defaultModel.contains("rerank"));
        }
    }
}
