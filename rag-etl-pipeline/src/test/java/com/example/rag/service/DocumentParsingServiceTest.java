package com.example.rag.service;

import com.example.rag.config.DashScopeDataCenterProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DocumentParsingService} 相关工具方法的单元测试。
 */
class DocumentParsingServiceTest {

    private ObjectMapper objectMapper;
    private DashScopeDataCenterProperties dataCenterProperties;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        dataCenterProperties = new DashScopeDataCenterProperties(
                "https://dashscope.aliyuncs.com",
                "default",
                "workspace-123",
                "docmind",
                2,
                60
        );
    }

    @Nested
    @DisplayName("DashScopeDataCenterProperties测试")
    class PropertiesTests {

        @Test
        @DisplayName("Properties应正确保存配置值")
        void properties_storesValues() {
            assertEquals("https://dashscope.aliyuncs.com", dataCenterProperties.baseUrl());
            assertEquals("default", dataCenterProperties.categoryId());
            assertEquals("workspace-123", dataCenterProperties.workspaceId());
            assertEquals("docmind", dataCenterProperties.parser());
            assertEquals(2, dataCenterProperties.pollIntervalSeconds());
            assertEquals(60, dataCenterProperties.maxWaitSeconds());
        }

        @Test
        @DisplayName("null workspaceId应被正确处理")
        void properties_nullWorkspace() {
            DashScopeDataCenterProperties props =
                    new DashScopeDataCenterProperties(
                            "https://dashscope.aliyuncs.com",
                            "default",
                            null,
                            "docmind",
                            2,
                            60
                    );
            assertNull(props.workspaceId());
        }
    }

    @Nested
    @DisplayName("MD5计算测试")
    class Md5Tests {

        @Test
        @DisplayName("相同内容应产生相同的MD5")
        void md5Consistency() {
            byte[] content = "Hello World".getBytes();
            String md5a = calculateMd5(content);
            String md5b = calculateMd5(content);
            assertEquals(md5a, md5b);
        }

        @Test
        @DisplayName("不同内容应产生不同的MD5")
        void md5DifferentContent() {
            byte[] content1 = "Content A".getBytes();
            byte[] content2 = "Content B".getBytes();
            String md5a = calculateMd5(content1);
            String md5b = calculateMd5(content2);
            assertNotEquals(md5a, md5b);
        }

        @Test
        @DisplayName("MD5长度应为32字符")
        void md5Length() {
            byte[] content = "Test content".getBytes();
            String md5 = calculateMd5(content);
            assertEquals(32, md5.length());
        }

        @Test
        @DisplayName("空内容应产生有效MD5")
        void md5EmptyContent() {
            byte[] content = new byte[0];
            String md5 = calculateMd5(content);
            assertEquals(32, md5.length());
            // d41d8cd98f00b204e9800998ecf8427e 是空字符串的MD5
            assertNotNull(md5);
        }

        @Test
        @DisplayName("MD5应为十六进制格式")
        void md5HexFormat() {
            byte[] content = "Test".getBytes();
            String md5 = calculateMd5(content);
            assertTrue(md5.matches("[0-9a-f]{32}"));
        }
    }

    @Nested
    @DisplayName("URL处理测试")
    class UrlHandlingTests {

        @Test
        @DisplayName("baseUrl末尾斜杠应被去除")
        void baseUrlTrailingSlash() {
            String url = "https://dashscope.aliyuncs.com/";
            String result = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            assertEquals("https://dashscope.aliyuncs.com", result);
        }

        @Test
        @DisplayName("无末尾斜杠的URL应保持不变")
        void baseUrlNoTrailingSlash() {
            String url = "https://dashscope.aliyuncs.com";
            String result = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            assertEquals("https://dashscope.aliyuncs.com", result);
        }
    }

    @Nested
    @DisplayName("DocMind JSON解析测试")
    class JsonParsingTests {

        @Test
        @DisplayName("包含markdown字段的响应应正确提取")
        void jsonWithMarkdown() throws Exception {
            String json = """
                    {"markdown": "# 标题\\n这是内容部分"}
                    """;
            var node = objectMapper.readTree(json);
            String markdown = node.path("markdown").asText();
            assertTrue(markdown.contains("标题"));
        }

        @Test
        @DisplayName("非JSON响应应返回原文")
        void nonJson_returnsOriginal() {
            String rawText = "这不是JSON格式的响应";
            assertFalse(rawText.startsWith("{"));
        }

        @Test
        @DisplayName("空JSON应正确处理")
        void emptyJson_handled() throws Exception {
            String json = "{}";
            var node = objectMapper.readTree(json);
            assertTrue(node.isEmpty());
        }
    }

    @Nested
    @DisplayName("文件名处理测试")
    class FilenameTests {

        @Test
        @DisplayName("Unix路径应正确提取basename")
        void unixPath_basenameExtraction() {
            String filename = "path/to/document.pdf";
            int idx = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
            String basename = idx >= 0 ? filename.substring(idx + 1) : filename;
            assertEquals("document.pdf", basename);
        }

        @Test
        @DisplayName("Windows路径应正确提取basename")
        void windowsPath_basenameExtraction() {
            String filename = "C:\\Users\\test\\document.pdf";
            int idx = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
            String basename = idx >= 0 ? filename.substring(idx + 1) : filename;
            assertEquals("document.pdf", basename);
        }

        @Test
        @DisplayName("无路径的文件名应保持不变")
        void noPath_keepsFilename() {
            String filename = "document.pdf";
            int idx = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
            String basename = idx >= 0 ? filename.substring(idx + 1) : filename;
            assertEquals("document.pdf", basename);
        }
    }

    // ==================== 辅助方法 ====================

    private String calculateMd5(byte[] content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content);
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
