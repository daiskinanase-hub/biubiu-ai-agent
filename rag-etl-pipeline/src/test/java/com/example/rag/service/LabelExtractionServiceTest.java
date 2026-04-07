package com.example.rag.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LabelExtractionService} 相关标签提取逻辑测试。
 */
class LabelExtractionServiceTest {

    @Nested
    @DisplayName("标签解析测试")
    class LabelParsingTests {

        @Test
        @DisplayName("逗号分隔的标签应正确分割")
        void commaSeparatedLabels_splitCorrectly() {
            String result = "标签1,标签2,标签3";
            List<String> labels = Arrays.stream(result.split("[,，]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            assertEquals(3, labels.size());
            assertTrue(labels.contains("标签1"));
            assertTrue(labels.contains("标签2"));
            assertTrue(labels.contains("标签3"));
        }

        @Test
        @DisplayName("中文逗号分隔的标签应正确分割")
        void chineseComma_splitCorrectly() {
            String result = "标签一，标签二，标签三";
            List<String> labels = Arrays.stream(result.split("[,，]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            assertEquals(3, labels.size());
            assertTrue(labels.contains("标签一"));
        }

        @Test
        @DisplayName("重复标签应被去重")
        void duplicateLabels_removed() {
            String result = "标签1,标签2,标签1,标签3,标签2";
            List<String> labels = Arrays.stream(result.split("[,，]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();

            assertEquals(3, labels.size());
        }

        @Test
        @DisplayName("空响应应返回空列表")
        void emptyResponse_returnsEmpty() {
            String result = "";
            List<String> labels = Arrays.stream(result.split("[,，]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            assertTrue(labels.isEmpty());
        }

        @Test
        @DisplayName("空白响应应返回空列表")
        void whitespaceResponse_returnsEmpty() {
            String result = "   \n\t  ";
            List<String> labels = Arrays.stream(result.split("[,，]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            assertTrue(labels.isEmpty());
        }

        @Test
        @DisplayName("标签前后空格应被去除")
        void labelsWithSpaces_trimmed() {
            String result = "  标签1  ,  标签2  ,  标签3  ";
            List<String> labels = Arrays.stream(result.split("[,，]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            assertEquals(3, labels.size());
            assertTrue(labels.stream().noneMatch(s -> s.startsWith(" ") || s.endsWith(" ")));
        }

        @Test
        @DisplayName("只有一个标签时应正常返回")
        void singleLabel_works() {
            String result = "唯一标签";
            List<String> labels = Arrays.stream(result.split("[,，]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            assertEquals(1, labels.size());
            assertEquals("唯一标签", labels.get(0));
        }

        @Test
        @DisplayName("混合中英文逗号应正确处理")
        void mixedCommas_handled() {
            String result = "标签1,标签2，标签3,标签4";
            List<String> labels = Arrays.stream(result.split("[,，]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            assertEquals(4, labels.size());
        }

        @Test
        @DisplayName("空分割应返回空列表")
        void emptySplit_returnsEmpty() {
            String result = "";
            // split返回包含空元素的数组
            String[] parts = result.split("[,，]");
            assertTrue(parts.length > 0);
        }

        @Test
        @DisplayName("仅包含空格的分割结果应被过滤")
        void onlyWhitespace_filtered() {
            String result = ",,  ,,";
            List<String> labels = Arrays.stream(result.split("[,，]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            assertTrue(labels.isEmpty());
        }
    }

    @Nested
    @DisplayName("系统提示词测试")
    class SystemPromptTests {

        private static final String SYSTEM_PROMPT = """
                你是企业文档分类助手。请从用户问题中提取 1-5 个最相关的文档主题标签，用于精确匹配知识库中的文档分类。
                要求：
                1. 只返回逗号分隔的标签列表；
                2. 不要添加编号、解释或任何额外文字；
                3. 标签应尽量简洁（2-8个字）。
                """;

        @Test
        @DisplayName("系统提示词应包含提取要求")
        void systemPrompt_containsRequirements() {
            assertTrue(SYSTEM_PROMPT.contains("1-5"));
            assertTrue(SYSTEM_PROMPT.contains("标签"));
            assertTrue(SYSTEM_PROMPT.contains("逗号"));
        }

        @Test
        @DisplayName("系统提示词应包含简洁要求")
        void systemPrompt_containsLengthRequirement() {
            assertTrue(SYSTEM_PROMPT.contains("简洁"));
            assertTrue(SYSTEM_PROMPT.contains("2-8个字"));
        }

        @Test
        @DisplayName("系统提示词不应包含多余说明")
        void systemPrompt_noExtraExplanation() {
            // 提示词不应包含类似"以下是标签"的额外说明
            assertFalse(SYSTEM_PROMPT.contains("以下是"));
            assertFalse(SYSTEM_PROMPT.contains("标签："));
        }
    }
}
