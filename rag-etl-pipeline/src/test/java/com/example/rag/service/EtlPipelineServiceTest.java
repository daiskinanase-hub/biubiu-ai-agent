package com.example.rag.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link EtlPipelineService} 单元测试：验证ETL管道服务的核心流程编排逻辑。
 */
@ExtendWith(MockitoExtension.class)
class EtlPipelineServiceTest {

    @Mock
    private DocumentParsingService documentParsingService;

    @Mock
    private SemanticChunkingService semanticChunkingService;

    @Mock
    private MetadataEnhancementService metadataEnhancementService;

    @Mock
    private VectorStore vectorStore;

    private EtlPipelineService service;

    @BeforeEach
    void setUp() {
        service = new EtlPipelineService(
                documentParsingService,
                semanticChunkingService,
                metadataEnhancementService,
                vectorStore
        );
    }

    @Nested
    @DisplayName("runPipeline方法测试")
    class RunPipelineTests {

        @Test
        @DisplayName("正常流程应返回处理切片数")
        void runPipeline_normalFlow_returnsChunkCount() throws IOException {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());
            String markdown = "# 测试文档\n这是测试内容";
            List<String> chunks = List.of("chunk1", "chunk2", "chunk3");

            when(documentParsingService.parseToMarkdown(any())).thenReturn(markdown);
            when(semanticChunkingService.chunkWithTenPercentOverlap(markdown)).thenReturn(chunks);
            when(metadataEnhancementService.enhance(anyString()))
                    .thenReturn(new com.example.rag.dto.ExtractedMetadata("l", "k", null, null));
            when(metadataEnhancementService.toMetadataMap(any(), anyString(), anyInt()))
                    .thenReturn(java.util.Map.of("doc_id", "test"));

            // When
            int result = service.runPipeline(file, "doc-123");

            // Then
            assertEquals(3, result);
            verify(documentParsingService).parseToMarkdown(file);
            verify(semanticChunkingService).chunkWithTenPercentOverlap(markdown);
            verify(vectorStore, atLeastOnce()).add(anyList());
        }

        @Test
        @DisplayName("空切片列表应返回0")
        void runPipeline_emptyChunks_returnsZero() throws IOException {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());
            String markdown = "# 测试";

            when(documentParsingService.parseToMarkdown(any())).thenReturn(markdown);
            when(semanticChunkingService.chunkWithTenPercentOverlap(markdown))
                    .thenReturn(List.of());

            // When
            int result = service.runPipeline(file, "doc-123");

            // Then
            assertEquals(0, result);
            verify(vectorStore, never()).add(anyList());
        }

        @Test
        @DisplayName("大批量数据应分批写入")
        void runPipeline_largeBatch_batchesWrites() throws IOException {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());
            String markdown = "# 内容";
            // 生成30个chunks（超过25条一批的限制）
            List<String> chunks = new java.util.ArrayList<>();
            for (int i = 0; i < 30; i++) {
                chunks.add("chunk" + i);
            }

            when(documentParsingService.parseToMarkdown(any())).thenReturn(markdown);
            when(semanticChunkingService.chunkWithTenPercentOverlap(markdown)).thenReturn(chunks);
            when(metadataEnhancementService.enhance(anyString()))
                    .thenReturn(new com.example.rag.dto.ExtractedMetadata("l", "k", null, null));
            when(metadataEnhancementService.toMetadataMap(any(), anyString(), anyInt()))
                    .thenReturn(java.util.Map.of("doc_id", "test"));

            // When
            int result = service.runPipeline(file, "doc-123");

            // Then
            assertEquals(30, result);
            // 30条分成两批：25条和5条
            verify(vectorStore, times(2)).add(anyList());
        }

        @Test
        @DisplayName("文档解析异常应向上传播")
        void runPipeline_parseException_propagates() throws IOException {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());

            when(documentParsingService.parseToMarkdown(any()))
                    .thenThrow(new IOException("文件读取失败"));

            // When & Then
            assertThrows(IOException.class, () ->
                    service.runPipeline(file, "doc-123"));
        }

        @Test
        @DisplayName("空文档内容应返回0")
        void runPipeline_nullContent_returnsZero() throws IOException {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());

            when(documentParsingService.parseToMarkdown(any())).thenReturn("");
            when(semanticChunkingService.chunkWithTenPercentOverlap(""))
                    .thenReturn(List.of());

            // When
            int result = service.runPipeline(file, "doc-123");

            // Then
            assertEquals(0, result);
        }
    }

    @Nested
    @DisplayName("并发增强测试")
    class ConcurrentEnhancementTests {

        @Test
        @DisplayName("多切片应并发处理")
        void runPipeline_multipleChunks_concurrentEnhancement() throws IOException {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());
            String markdown = "# 内容";
            List<String> chunks = List.of("chunk1", "chunk2", "chunk3", "chunk4");

            when(documentParsingService.parseToMarkdown(any())).thenReturn(markdown);
            when(semanticChunkingService.chunkWithTenPercentOverlap(markdown)).thenReturn(chunks);
            when(metadataEnhancementService.enhance(anyString()))
                    .thenReturn(new com.example.rag.dto.ExtractedMetadata("l", "k", null, null));
            when(metadataEnhancementService.toMetadataMap(any(), anyString(), anyInt()))
                    .thenReturn(java.util.Map.of("doc_id", "test"));

            // When
            int result = service.runPipeline(file, "doc-123");

            // Then
            assertEquals(4, result);
            // 验证enhance被调用4次
            verify(metadataEnhancementService, times(4)).enhance(anyString());
        }

        @Test
        @DisplayName("切片的metadata转换应被调用")
        void runPipeline_metadataConversion_called() throws IOException {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());
            String markdown = "# 内容";
            List<String> chunks = List.of("chunkA", "chunkB");

            when(documentParsingService.parseToMarkdown(any())).thenReturn(markdown);
            when(semanticChunkingService.chunkWithTenPercentOverlap(markdown)).thenReturn(chunks);
            when(metadataEnhancementService.enhance(anyString()))
                    .thenReturn(new com.example.rag.dto.ExtractedMetadata("l", "k", null, null));
            when(metadataEnhancementService.toMetadataMap(any(), anyString(), anyInt()))
                    .thenReturn(java.util.Map.of("doc_id", "test"));

            // When
            service.runPipeline(file, "doc-123");

            // Then - 验证toMetadataMap被调用次数等于chunk数量
            verify(metadataEnhancementService, times(2)).toMetadataMap(any(), eq("doc-123"), anyInt());
        }
    }

    @Nested
    @DisplayName("依赖注入测试")
    class DependencyInjectionTests {

        @Test
        @DisplayName("服务应正确注入所有依赖")
        void constructor_allDependenciesInjected() {
            EtlPipelineService svc = new EtlPipelineService(
                    documentParsingService,
                    semanticChunkingService,
                    metadataEnhancementService,
                    vectorStore
            );
            assertNotNull(svc);
        }

        @Test
        @DisplayName("null DocumentParsingService应被接受")
        void constructor_nullParsingService_accepted() {
            EtlPipelineService svc = new EtlPipelineService(
                    null,
                    semanticChunkingService,
                    metadataEnhancementService,
                    vectorStore
            );
            assertNotNull(svc);
        }
    }
}
