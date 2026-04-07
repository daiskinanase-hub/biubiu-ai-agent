package com.example.rag.controller;

import com.example.rag.dto.RagQueryRequest;
import com.example.rag.dto.RagQueryResponse;
import com.example.rag.service.RagQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link RagQueryController} 单元测试：验证RAG查询控制器的请求转发逻辑。
 */
@ExtendWith(MockitoExtension.class)
class RagQueryControllerTest {

    @Mock
    private RagQueryService ragQueryService;

    private RagQueryController controller;

    @BeforeEach
    void setUp() {
        controller = new RagQueryController(ragQueryService);
    }

    @Nested
    @DisplayName("query方法测试")
    class QueryMethodTests {

        @Test
        @DisplayName("正常查询应返回AI回答")
        void query_validRequest_returnsAnswer() {
            // Given
            RagQueryRequest request = new RagQueryRequest("doc-123", "如何做某事");
            RagQueryResponse expectedResponse = new RagQueryResponse("这是AI的回答", 3, null);

            when(ragQueryService.query(any(RagQueryRequest.class))).thenReturn(expectedResponse);

            // When
            RagQueryResponse response = controller.query(request);

            // Then
            assertNotNull(response);
            assertEquals("这是AI的回答", response.answer());
            assertEquals(3, response.docCount());
            assertNull(response.error());
        }

        @Test
        @DisplayName("服务异常时应返回错误响应")
        void query_serviceException_returnsErrorResponse() {
            // Given
            RagQueryRequest request = new RagQueryRequest("doc-456", "问题");
            RagQueryResponse errorResponse = new RagQueryResponse(null, 0, "数据库连接失败");

            when(ragQueryService.query(any(RagQueryRequest.class))).thenReturn(errorResponse);

            // When
            RagQueryResponse response = controller.query(request);

            // Then
            assertNotNull(response);
            assertNull(response.answer());
            assertEquals(0, response.docCount());
            assertEquals("数据库连接失败", response.error());
        }

        @Test
        @DisplayName("查询应正确传递参数")
        void query_passesCorrectParameters() {
            // Given
            String documentId = "test-doc";
            String prompt = "测试问题";
            RagQueryRequest request = new RagQueryRequest(documentId, prompt);
            RagQueryResponse expectedResponse = new RagQueryResponse("回答", 1, null);

            when(ragQueryService.query(any(RagQueryRequest.class))).thenReturn(expectedResponse);

            // When
            controller.query(request);

            // Then
            verify(ragQueryService).query(argThat(req ->
                    req.documentId().equals(documentId) && req.prompt().equals(prompt)));
        }

        @Test
        @DisplayName("空回答应正常返回")
        void query_emptyAnswer_returnsNormally() {
            // Given
            RagQueryRequest request = new RagQueryRequest("doc-789", "无法回答的问题");
            RagQueryResponse expectedResponse = new RagQueryResponse("", 0, null);

            when(ragQueryService.query(any(RagQueryRequest.class))).thenReturn(expectedResponse);

            // When
            RagQueryResponse response = controller.query(request);

            // Then
            assertNotNull(response);
            assertEquals("", response.answer());
        }

        @Test
        @DisplayName("零文档命中应正常返回")
        void query_zeroDocCount_returnsNormally() {
            // Given
            RagQueryRequest request = new RagQueryRequest("doc-empty", "无相关内容的问题");
            RagQueryResponse expectedResponse = new RagQueryResponse("根据通用知识回答...", 0, null);

            when(ragQueryService.query(any(RagQueryRequest.class))).thenReturn(expectedResponse);

            // When
            RagQueryResponse response = controller.query(request);

            // Then
            assertNotNull(response);
            assertEquals(0, response.docCount());
        }
    }

    @Nested
    @DisplayName("依赖注入测试")
    class DependencyInjectionTests {

        @Test
        @DisplayName("服务应正确注入")
        void constructor_injectsService() {
            RagQueryController ctrl = new RagQueryController(ragQueryService);
            assertNotNull(ctrl);
        }
    }
}
