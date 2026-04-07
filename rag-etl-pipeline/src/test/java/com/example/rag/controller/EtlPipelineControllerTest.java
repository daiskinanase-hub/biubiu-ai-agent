package com.example.rag.controller;

import com.example.rag.dto.EtlProcessResponse;
import com.example.rag.service.EtlPipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link EtlPipelineController} 单元测试：验证ETL管道控制器的文件上传和流程触发逻辑。
 */
@ExtendWith(MockitoExtension.class)
class EtlPipelineControllerTest {

    @Mock
    private EtlPipelineService etlPipelineService;

    private EtlPipelineController controller;

    @BeforeEach
    void setUp() {
        controller = new EtlPipelineController(etlPipelineService);
    }

    @Nested
    @DisplayName("process方法测试")
    class ProcessMethodTests {

        @Test
        @DisplayName("正常流程应返回成功响应")
        void process_validFile_returnsSuccessResponse() throws IOException {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "PDF content".getBytes());
            String documentId = "doc-123";
            int chunkCount = 5;

            when(etlPipelineService.runPipeline(any(), eq(documentId))).thenReturn(chunkCount);

            // When
            EtlProcessResponse response = controller.process(file, documentId);

            // Then
            assertEquals("success", response.status());
            assertEquals(documentId, response.documentId());
            assertEquals("test.pdf", response.documentName());
            assertEquals(chunkCount, response.chunksProcessed());
            assertNotNull(response.message());
            assertTrue(response.message().contains("successfully"));
        }

        @Test
        @DisplayName("文件名应使用原始文件名")
        void process_usesOriginalFilename() throws IOException {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "my-document.pdf", "application/pdf", "content".getBytes());
            String documentId = "doc-456";

            when(etlPipelineService.runPipeline(any(), eq(documentId))).thenReturn(3);

            // When
            EtlProcessResponse response = controller.process(file, documentId);

            // Then
            assertEquals("my-document.pdf", response.documentName());
        }

        @Test
        @DisplayName("documentId应被trim处理后传递给服务")
        void process_trimsDocumentId() throws IOException {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());
            String documentIdWithSpaces = "  doc-789  ";
            String trimmedId = "doc-789";

            when(etlPipelineService.runPipeline(any(), eq(trimmedId))).thenReturn(2);

            // When
            EtlProcessResponse response = controller.process(file, documentIdWithSpaces);

            // Then - 服务收到的是trim后的值
            verify(etlPipelineService).runPipeline(any(), eq(trimmedId));
        }

        @Test
        @DisplayName("documentId在响应中为原始值（trim已在传递给服务前完成）")
        void process_responseContainsOriginalDocumentId() throws IOException {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());
            String documentId = "doc-123";

            when(etlPipelineService.runPipeline(any(), anyString())).thenReturn(1);

            // When
            EtlProcessResponse response = controller.process(file, documentId);

            // Then - 响应中的documentId是原始值
            assertEquals(documentId, response.documentId());
        }

        @Test
        @DisplayName("空文件应抛出异常")
        void process_emptyFile_throwsException() {
            // Given
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", new byte[0]);
            String documentId = "doc-123";

            // When & Then
            assertThrows(IllegalArgumentException.class, () ->
                    controller.process(emptyFile, documentId));
        }

        @Test
        @DisplayName("null文件应抛出异常")
        void process_nullFile_throwsException() {
            // Given
            String documentId = "doc-123";

            // When & Then
            assertThrows(IllegalArgumentException.class, () ->
                    controller.process(null, documentId));
        }

        @Test
        @DisplayName("空documentId应抛出异常")
        void process_emptyDocumentId_throwsException() {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());

            // When & Then
            assertThrows(IllegalArgumentException.class, () ->
                    controller.process(file, ""));
        }

        @Test
        @DisplayName("空白documentId应抛出异常")
        void process_whitespaceDocumentId_throwsException() {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());

            // When & Then
            assertThrows(IllegalArgumentException.class, () ->
                    controller.process(file, "   \n\t  "));
        }

        @Test
        @DisplayName("ETL服务返回0应正常处理")
        void process_zeroChunks_handledGracefully() throws IOException {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());
            String documentId = "doc-123";

            when(etlPipelineService.runPipeline(any(), eq(documentId))).thenReturn(0);

            // When
            EtlProcessResponse response = controller.process(file, documentId);

            // Then
            assertEquals("success", response.status());
            assertEquals(0, response.chunksProcessed());
        }

        @Test
        @DisplayName("文件名使用cleanPath处理（路径可能保留）")
        void process_filenameWithPath_usesCleanPath() throws IOException {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "path/to/test.pdf", "application/pdf", "content".getBytes());
            String documentId = "doc-123";

            when(etlPipelineService.runPipeline(any(), eq(documentId))).thenReturn(1);

            // When
            EtlProcessResponse response = controller.process(file, documentId);

            // Then - cleanPath可能保留路径
            assertNotNull(response.documentName());
            assertTrue(response.documentName().endsWith("test.pdf"));
        }

        @Test
        @DisplayName("文件名包含特殊字符应正常处理")
        void process_filenameWithSpecialChars_handledGracefully() throws IOException {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "我的文档 (2024).pdf", "application/pdf", "content".getBytes());
            String documentId = "doc-123";

            when(etlPipelineService.runPipeline(any(), eq(documentId))).thenReturn(1);

            // When
            EtlProcessResponse response = controller.process(file, documentId);

            // Then
            assertEquals("我的文档 (2024).pdf", response.documentName());
        }

        @Test
        @DisplayName("IOException应向上传播")
        void process_ioException_propagates() throws IOException {
            // Given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "content".getBytes());
            String documentId = "doc-123";

            when(etlPipelineService.runPipeline(any(), eq(documentId)))
                    .thenThrow(new IOException("文件读取失败"));

            // When & Then
            assertThrows(IOException.class, () ->
                    controller.process(file, documentId));
        }
    }

    @Nested
    @DisplayName("依赖注入测试")
    class DependencyInjectionTests {

        @Test
        @DisplayName("服务应正确注入")
        void constructor_injectsService() {
            EtlPipelineController ctrl = new EtlPipelineController(etlPipelineService);
            assertNotNull(ctrl);
        }
    }
}
