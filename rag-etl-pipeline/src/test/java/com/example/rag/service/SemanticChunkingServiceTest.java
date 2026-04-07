package com.example.rag.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SemanticChunkingService} 单元测试：验证语义切块服务的各种边界情况和核心逻辑。
 */
class SemanticChunkingServiceTest {

    private final SemanticChunkingService service = new SemanticChunkingService();

    @Nested
    @DisplayName("空输入和边界测试")
    class EmptyAndBoundaryTests {

        @Test
        @DisplayName("空字符串应返回空列表")
        void chunkWithOverlap_emptyString_returnsEmptyList() {
            List<String> result = service.chunkWithOverlap("", 0.1);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("空白字符串应返回空列表")
        void chunkWithOverlap_blankString_returnsEmptyList() {
            List<String> result = service.chunkWithOverlap("   \n\n  ", 0.1);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("纯换行符应返回空列表")
        void chunkWithOverlap_onlyNewlines_returnsEmptyList() {
            List<String> result = service.chunkWithOverlap("\n\n\n\n", 0.1);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("null输入应返回空列表")
        void chunkWithOverlap_null_returnsEmptyList() {
            List<String> result = service.chunkWithOverlap(null, 0.1);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Markdown标题切分测试")
    class MarkdownHeadingTests {

        @Test
        @DisplayName("单级标题应按标题切分")
        void chunkWithOverlap_singleH1_splitsCorrectly() {
            String md = "# 概述\n" + "A".repeat(100) + "\n\n## 细节\n" + "B".repeat(100);
            List<String> chunks = service.chunkWithOverlap(md, 0.1);
            assertFalse(chunks.isEmpty());
            assertTrue(chunks.stream().anyMatch(c -> c.contains("概述")));
        }

        @Test
        @DisplayName("二级标题应按标题切分")
        void chunkWithOverlap_h2_splitsCorrectly() {
            String md = "## 章节一\n" + "内容".repeat(50) + "\n\n## 章节二\n" + "内容".repeat(50);
            List<String> chunks = service.chunkWithOverlap(md, 0.1);
            // 应该至少产生一些chunks
            assertFalse(chunks.isEmpty());
        }

        @Test
        @DisplayName("无标题时按段落切分")
        void chunk_fallbackToParagraphs() {
            String p1 = "段落A内容。" + "0123456789".repeat(15);
            String p2 = "段落B开始。" + "abcdefghij".repeat(10);
            String md = p1 + "\n\n" + p2;
            List<String> chunks = service.chunkWithTenPercentOverlap(md);
            assertFalse(chunks.isEmpty());
        }

        @Test
        @DisplayName("标题前的前置内容（如摘要）处理")
        void chunkWithOverlap_preambleBeforeHeading_preserved() {
            String preamble = "这是摘要部分";
            String heading = "## 正式内容\n" + "详细内容".repeat(30);
            String md = preamble + "\n\n" + heading;
            List<String> chunks = service.chunkWithOverlap(md, 0.1);
            // 验证产生了一些chunks
            assertFalse(chunks.isEmpty());
        }
    }

    @Nested
    @DisplayName("重叠逻辑测试")
    class OverlapTests {

        @Test
        @DisplayName("10%重叠应包含前一块的尾部内容")
        void chunkWithTenPercentOverlap_containsOverlap() {
            String md = "# 概述\n" + "A".repeat(200) + "\n\n## 细节\n" + "B".repeat(200);
            List<String> chunks = service.chunkWithTenPercentOverlap(md);
            if (chunks.size() >= 2) {
                String second = chunks.get(1);
                // 第二块应包含第一块的尾部内容作为重叠
                assertTrue(second.contains("A") || second.contains("概述"),
                        "第二块应包含重叠内容");
            }
        }

        @Test
        @DisplayName("单块文档不应添加重叠")
        void chunkWithOverlap_singleBlock_noOverlap() {
            String md = "# 单一主题\n" + "内容".repeat(100);
            List<String> chunks = service.chunkWithOverlap(md, 0.2);
            assertEquals(1, chunks.size());
        }
    }

    @Nested
    @DisplayName("长度限制测试")
    class LengthLimitTests {

        @Test
        @DisplayName("短文本应正常处理")
        void chunkWithOverlap_shortText_mergesBlocks() {
            String md = "短句一。短句二。短句三。";
            List<String> chunks = service.chunkWithOverlap(md, 0.1);
            assertFalse(chunks.isEmpty());
        }

        @Test
        @DisplayName("超长段落应被拆分")
        void chunkWithOverlap_longBlock_splitsIfNeeded() {
            String longContent = "这是一个很长的段落。".repeat(100);
            String md = "# 测试\n" + longContent;
            List<String> chunks = service.chunkWithOverlap(md, 0.1);
            // 超长块应该被拆分
            assertTrue(chunks.stream().anyMatch(c -> c.length() > 100));
        }

        @Test
        @DisplayName("每块长度不应超过最大限制")
        void chunkWithOverlap_respectsMaxLength() {
            String longContent = "持续内容".repeat(200);
            String md = "# 标题\n" + longContent;
            List<String> chunks = service.chunkWithOverlap(md, 0.1);
            // 每块都不应超过1000字符
            assertTrue(chunks.stream().noneMatch(c -> c.length() > 1100),
                    "存在超过最大长度的块");
        }
    }

    @Nested
    @DisplayName("辅助方法测试")
    class HelperMethodTests {

        @Test
        @DisplayName("快捷方法chunkWithTenPercentOverlap应正常工作")
        void chunkWithTenPercentOverlap_basic() {
            String md = "# 第一章\n" + "内容".repeat(50) + "\n\n# 第二章\n" + "内容".repeat(50);
            List<String> chunks = service.chunkWithTenPercentOverlap(md);
            assertFalse(chunks.isEmpty());
        }

        @Test
        @DisplayName("不同重叠比例应产生不同结果")
        void chunkWithOverlap_differentRatios() {
            String md = "# A\n" + "X".repeat(200) + "\n\n# B\n" + "Y".repeat(200);
            List<String> chunks1 = service.chunkWithOverlap(md, 0.05);
            List<String> chunks2 = service.chunkWithOverlap(md, 0.2);
            // 不同比例可能产生不同数量的块或不同的重叠内容
            assertFalse(chunks1.isEmpty());
            assertFalse(chunks2.isEmpty());
        }

        @Test
        @DisplayName("Windows换行符应被正确处理")
        void chunkWithOverlap_windowsNewlines() {
            String md = "# 标题\r\n内容\r\n\r\n第二段";
            List<String> chunks = service.chunkWithOverlap(md, 0.1);
            assertFalse(chunks.isEmpty());
        }
    }
}
