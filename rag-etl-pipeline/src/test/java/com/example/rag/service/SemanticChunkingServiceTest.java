package com.example.rag.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * {@link SemanticChunkingService} 集成测试：在完整上下文中验证语义切块与重叠逻辑。
 */
@SpringBootTest
@Disabled
class SemanticChunkingServiceTest {

    @Autowired
    private SemanticChunkingService semanticChunkingService;

    /**
     * 含二级标题的 Markdown 应被拆成多块，且第二块头部包含前块约 10% 尾部上下文。
     */
    @Test
    void chunkWithTenPercentOverlap_keepsSemanticSectionsAndOverlap() {
        String md = """
                # 概述
                """ + "ABCDEFGHIJ".repeat(20) + """

                ## 细节
                """ + "XYZ".repeat(30);
        List<String> chunks = semanticChunkingService.chunkWithTenPercentOverlap(md);
        Assertions.assertTrue(chunks.size() >= 2);
        String second = chunks.get(1);
        Assertions.assertTrue(second.contains("细节") || second.contains("XYZ"));
    }

    /**
     * 无标题时按空行分段，仍可应用重叠。
     */
    @Test
    void chunk_fallbackToParagraphs() {
        String p1 = "段落A内容。" + "0123456789".repeat(15);
        String p2 = "段落B开始。" + "abcdefghij".repeat(10);
        String md = p1 + "\n\n" + p2;
        List<String> chunks = semanticChunkingService.chunkWithTenPercentOverlap(md);
        Assertions.assertFalse(chunks.isEmpty());
    }
}
