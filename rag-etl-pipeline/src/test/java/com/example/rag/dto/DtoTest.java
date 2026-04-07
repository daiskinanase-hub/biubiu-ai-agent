package com.example.rag.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DTO类单元测试：验证数据传输对象的构造、默认值和序列化特性。
 */
class DtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("RagQueryRequest测试")
    class RagQueryRequestTests {

        @Test
        @DisplayName("正常构造应包含所有字段")
        void constructor_containsAllFields() {
            RagQueryRequest request = new RagQueryRequest("doc-123", "用户问题");

            assertEquals("doc-123", request.documentId());
            assertEquals("用户问题", request.prompt());
        }

        @Test
        @DisplayName("record应支持equals比较")
        void record_supportsEquals() {
            RagQueryRequest r1 = new RagQueryRequest("doc", "问题");
            RagQueryRequest r2 = new RagQueryRequest("doc", "问题");
            RagQueryRequest r3 = new RagQueryRequest("doc", "不同问题");

            assertEquals(r1, r2);
            assertNotEquals(r1, r3);
        }

        @Test
        @DisplayName("record应支持hashCode")
        void record_supportsHashCode() {
            RagQueryRequest r1 = new RagQueryRequest("doc", "问题");
            RagQueryRequest r2 = new RagQueryRequest("doc", "问题");

            assertEquals(r1.hashCode(), r2.hashCode());
        }

        @Test
        @DisplayName("record应支持toString")
        void record_supportsToString() {
            RagQueryRequest request = new RagQueryRequest("doc", "问题");
            String str = request.toString();

            assertTrue(str.contains("doc"));
            assertTrue(str.contains("问题"));
        }
    }

    @Nested
    @DisplayName("RagQueryResponse测试")
    class RagQueryResponseTests {

        @Test
        @DisplayName("正常构造应包含所有字段")
        void constructor_containsAllFields() {
            RagQueryResponse response = new RagQueryResponse("AI回答", 5, null);

            assertEquals("AI回答", response.answer());
            assertEquals(5, response.docCount());
            assertNull(response.error());
        }

        @Test
        @DisplayName("错误响应应包含错误信息")
        void errorResponse_containsErrorMessage() {
            RagQueryResponse response = new RagQueryResponse(null, 0, "服务异常");

            assertNull(response.answer());
            assertEquals(0, response.docCount());
            assertEquals("服务异常", response.error());
        }
    }

    @Nested
    @DisplayName("EtlProcessResponse测试")
    class EtlProcessResponseTests {

        @Test
        @DisplayName("正常构造应包含所有字段")
        void constructor_containsAllFields() {
            EtlProcessResponse response = new EtlProcessResponse(
                    "success", "doc-123", "test.pdf", 10, "处理完成");

            assertEquals("success", response.status());
            assertEquals("doc-123", response.documentId());
            assertEquals("test.pdf", response.documentName());
            assertEquals(10, response.chunksProcessed());
            assertEquals("处理完成", response.message());
        }

        @Test
        @DisplayName("零切片数应正常表示")
        void zeroChunks_representedCorrectly() {
            EtlProcessResponse response = new EtlProcessResponse(
                    "success", "doc", "empty.pdf", 0, "无内容");

            assertEquals(0, response.chunksProcessed());
        }
    }

    @Nested
    @DisplayName("SseEvent测试")
    class SseEventTests {

        @Test
        @DisplayName("正常构造应包含所有字段")
        void constructor_containsAllFields() {
            SseEvent event = new SseEvent("chunk", "内容片段");

            assertEquals("chunk", event.type());
            assertEquals("内容片段", event.content());
        }

        @Test
        @DisplayName("done类型事件content可为null")
        void doneType_contentCanBeNull() {
            SseEvent event = new SseEvent("done", null);

            assertEquals("done", event.type());
            assertNull(event.content());
        }

        @Test
        @DisplayName("error类型事件应包含错误信息")
        void errorType_containsErrorMessage() {
            SseEvent event = new SseEvent("error", "网络连接失败");

            assertEquals("error", event.type());
            assertEquals("网络连接失败", event.content());
        }
    }

    @Nested
    @DisplayName("ExtractedMetadata测试")
    class ExtractedMetadataTests {

        @Test
        @DisplayName("正常构造应包含所有字段")
        void constructor_containsAllFields() {
            ExtractedMetadata meta = new ExtractedMetadata("标签", "kw1,kw2", 5, "相关问题");

            assertEquals("标签", meta.label());
            assertEquals("kw1,kw2", meta.keyword());
            assertEquals(5, meta.pageNumber());
            assertEquals("相关问题", meta.presetQuestion());
        }

        @Test
        @DisplayName("null字段应正常表示")
        void nullFields_representedCorrectly() {
            ExtractedMetadata meta = new ExtractedMetadata(null, null, null, null);

            assertNull(meta.label());
            assertNull(meta.keyword());
            assertNull(meta.pageNumber());
            assertNull(meta.presetQuestion());
        }

        @Test
        @DisplayName("JSON属性名应正确映射")
        void jsonPropertyNames_mappedCorrectly() throws Exception {
            ExtractedMetadata meta = new ExtractedMetadata("标签", "关键词", 10, "问题");
            String json = objectMapper.writeValueAsString(meta);

            assertTrue(json.contains("\"label\":\"标签\""));
            assertTrue(json.contains("\"keyword\":\"关键词\""));
            assertTrue(json.contains("\"page_number\":10"));
            assertTrue(json.contains("\"preset_question\":\"问题\""));
        }
    }

    @Nested
    @DisplayName("EtlRequest测试")
    class EtlRequestTests {

        @Test
        @DisplayName("正常构造应包含所有字段")
        void constructor_containsAllFields() {
            org.springframework.mock.web.MockMultipartFile file =
                    new org.springframework.mock.web.MockMultipartFile(
                            "file", "test.pdf", "application/pdf", "content".getBytes());

            EtlRequest request = new EtlRequest("doc-123", file);

            assertEquals("doc-123", request.documentId());
            assertEquals(file, request.file());
        }

        @Test
        @DisplayName("null文件应正常表示")
        void nullFile_representedCorrectly() {
            EtlRequest request = new EtlRequest("doc", null);

            assertEquals("doc", request.documentId());
            assertNull(request.file());
        }
    }
}
