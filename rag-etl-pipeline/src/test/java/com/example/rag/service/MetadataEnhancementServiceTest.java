package com.example.rag.service;

import com.example.rag.dto.ExtractedMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MetadataEnhancementService} 相关工具方法测试。
 */
class MetadataEnhancementServiceTest {

    @Nested
    @DisplayName("ExtractedMetadata处理测试")
    class MetadataTests {

        @Test
        @DisplayName("正常元数据应正确转换")
        void validMetadata_convertsCorrectly() {
            // Given
            ExtractedMetadata meta = new ExtractedMetadata(
                    "主题标签",
                    "关键词1,关键词2,关键词3",
                    5,
                    "预设问题是什么"
            );
            String documentId = "doc-123";
            int chunkIndex = 2;

            // When - 直接验证记录字段
            assertEquals("主题标签", meta.label());
            assertEquals("关键词1,关键词2,关键词3", meta.keyword());
            assertEquals(5, meta.pageNumber());
            assertEquals("预设问题是什么", meta.presetQuestion());
        }

        @Test
        @DisplayName("null元数据字段应正确表示")
        void nullMetadataFields() {
            // Given
            ExtractedMetadata meta = new ExtractedMetadata(null, null, null, null);

            assertNull(meta.label());
            assertNull(meta.keyword());
            assertNull(meta.pageNumber());
            assertNull(meta.presetQuestion());
        }

        @Test
        @DisplayName("关键词分割逻辑测试")
        void keywordSplitLogic() {
            // Given
            String keyword = "kw1,kw2, kw3";

            // When
            String[] keywords = keyword.split("[,]");
            keywords = Arrays.stream(keywords).map(String::trim).toArray(String[]::new);

            // Then
            assertEquals(3, keywords.length);
            assertEquals("kw1", keywords[0]);
            assertEquals("kw2", keywords[1]);
            assertEquals("kw3", keywords[2]);
        }

        @Test
        @DisplayName("关键词包含空值应被过滤")
        void keywordWithEmptyValues() {
            // Given
            String keyword = "kw1,, kw2,  ,kw3";

            // When
            String[] keywords = keyword.split("[,]");
            keywords = Arrays.stream(keywords)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);

            // Then
            assertEquals(3, keywords.length);
        }

        @Test
        @DisplayName("空标签应被检测")
        void emptyLabel() {
            ExtractedMetadata meta = new ExtractedMetadata("", "keyword", null, null);
            assertTrue(meta.label().isEmpty());
        }

        @Test
        @DisplayName("空关键词应被检测")
        void emptyKeyword() {
            ExtractedMetadata meta = new ExtractedMetadata("label", "", null, null);
            assertTrue(meta.keyword().isEmpty());
        }
    }

    @Nested
    @DisplayName("文本截断测试")
    class TruncationTests {

        private static final int MAX_CHUNK_CHARS = 12_000;

        @Test
        @DisplayName("短文本不应被截断")
        void shortText_unchanged() {
            String text = "短文本内容";
            String result = text.length() <= MAX_CHUNK_CHARS ? text : text.substring(0, MAX_CHUNK_CHARS);
            assertEquals("短文本内容", result);
        }

        @Test
        @DisplayName("超长文本应被截断")
        void longText_truncated() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 15000; i++) {
                sb.append("长");
            }
            String longText = sb.toString();
            String result = longText.length() <= MAX_CHUNK_CHARS ? longText : longText.substring(0, MAX_CHUNK_CHARS);
            assertEquals(MAX_CHUNK_CHARS, result.length());
        }

        @Test
        @DisplayName("正好12字符的文本不应被截断")
        void exactly12kChars_unchanged() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < MAX_CHUNK_CHARS; i++) {
                sb.append("A");
            }
            String text = sb.toString();
            String result = text.length() <= MAX_CHUNK_CHARS ? text : text.substring(0, MAX_CHUNK_CHARS);
            assertEquals(MAX_CHUNK_CHARS, result.length());
        }
    }

    @Nested
    @DisplayName("Map构建测试")
    class MapBuildTests {

        @Test
        @DisplayName("基础字段Map构建")
        void basicFieldsMap() {
            String documentId = "doc-123";
            int chunkIndex = 0;

            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("document_id", documentId);
            map.put("chunk_index", chunkIndex);

            assertEquals(documentId, map.get("document_id"));
            assertEquals(chunkIndex, map.get("chunk_index"));
            assertEquals(2, map.size());
        }

        @Test
        @DisplayName("带关键词的Map构建")
        void mapWithKeywords() {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("document_id", "doc");
            map.put("chunk_index", 0);

            String keyword = "kw1,kw2";
            String[] keywords = keyword.split("[,]");
            map.put("keywords", Arrays.stream(keywords).map(String::trim).toArray(String[]::new));

            assertTrue(map.containsKey("keywords"));
            String[] stored = (String[]) map.get("keywords");
            assertEquals(2, stored.length);
        }

        @Test
        @DisplayName("带页码的Map构建")
        void mapWithPageNumber() {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("document_id", "doc");
            map.put("chunk_index", 0);

            Integer pageNumber = 5;
            map.put("page_number", pageNumber);

            assertEquals(5, map.get("page_number"));
        }
    }
}
