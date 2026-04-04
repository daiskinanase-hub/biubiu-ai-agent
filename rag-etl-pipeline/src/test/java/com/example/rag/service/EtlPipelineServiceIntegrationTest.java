package com.example.rag.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 全链路 ETL 集成测试：依赖可连通的 PostgreSQL（含 vector_store 表）、有效的 DashScope Key 与 DataCenter 类目权限。
 * <p>默认禁用，避免在 CI/本地无密钥时失败；需验证时去掉 {@link Disabled} 并提供真实 PDF。</p>
 */
@SpringBootTest
@Disabled("需配置 DASHSCOPE_API_KEY、DataCenter 类目及本地 Postgres 后手动启用")
class EtlPipelineServiceIntegrationTest {

    @Autowired
    private EtlPipelineService etlPipelineService;

    @Autowired
    private DocumentParsingService documentParsingService;

    /**
     * 从 test resources 读取真实 PDF，调用 {@link EtlPipelineService#runPipeline} 并断言成功切片数。
     * 同时将解析后的 Markdown 输出到 src/test/resources/parsed_output.md 以便人工校验。
     */
    @Test
    void runPipeline_realPdf_placesChunksInVectorStore() throws Exception {
        ClassPathResource resource = new ClassPathResource("sample.pdf");
        Assertions.assertTrue(resource.exists(), "请先放置 src/test/resources/sample.pdf 真实样例文件");
        byte[] bytes;
        // 读取类路径中的真实 PDF 字节，避免使用伪造头部字符串导致解析失败
        try (InputStream inputStream = resource.getInputStream()) {
            bytes = StreamUtils.copyToByteArray(inputStream);
        }
        Assertions.assertTrue(bytes.length > 0, "sample.pdf 不能为空");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                bytes);

        // 生成 Markdown 并落盘到 test resources，供验证
//        String markdown = documentParsingService.parseToMarkdown(file);
//        Path outputPath = Paths.get("src/test/resources/parsed_output.md");
//        Files.writeString(outputPath, markdown, StandardCharsets.UTF_8);

        int n = etlPipelineService.runPipeline(file, "integration-doc-1");
        Assertions.assertTrue(n > 0, "真实文档处理后应至少产生 1 个切片");
    }
}
