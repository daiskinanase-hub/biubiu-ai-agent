package com.example.rag.controller;

import com.example.rag.dto.RagQueryRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * RagQueryController 集成测试：手动构造 RagQueryRequest 并直接调用 Controller 方法。
 * <p>默认禁用，避免在 CI/本地无密钥时失败；需验证时去掉 {@link Disabled} 并确保 PostgreSQL、DashScope API Key 可用。</p>
 */
@SpringBootTest
@Disabled("需配置 DASHSCOPE_API_KEY、本地 Postgres 及文档数据后手动启用")
class RagQueryControllerIntegrationTest {

    @Autowired
    private RagQueryController ragQueryController;

    @Test
    void query_directCall_shouldReturnResponse() throws Exception {
        // 1. 手动拼接请求
        RagQueryRequest request = new RagQueryRequest("integration-doc-1", "如何丰富小说剧情");
        System.out.println("[Test] 用户初始查询 | documentId=" + request.documentId() + " | prompt=" + request.prompt());

        // 2. 直接调用 Controller 里的方法（当前为非流式同步接口）
        var response = ragQueryController.query(request);

        // 3. 打印响应结果
        System.out.println("[Test] RAG 查询响应 | docCount=" + response.docCount() + " | error=" + response.error());
        String answerPreview = response.answer() == null
                ? "null"
                : response.answer().substring(0, Math.min(200, response.answer().length()));
        System.out.println("[Test] answerPreview=" + answerPreview);

        // 4. 断言
        Assertions.assertNotNull(response, "响应不应为空");
        Assertions.assertTrue(response.docCount() >= 0, "docCount 应大于等于 0");
    }
}
