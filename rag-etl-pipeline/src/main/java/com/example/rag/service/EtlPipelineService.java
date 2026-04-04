package com.example.rag.service;

import com.example.rag.dto.ExtractedMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * ETL 主编排：解析 → 语义切片（10% 重叠）→ 并发元数据增强 → 批量写入 {@link VectorStore}。
 */
@Service
public class EtlPipelineService {

    private static final Logger log = LoggerFactory.getLogger(EtlPipelineService.class);

    private final DocumentParsingService documentParsingService;
    private final SemanticChunkingService semanticChunkingService;
    private final MetadataEnhancementService metadataEnhancementService;
    private final VectorStore vectorStore;

    /**
     * 注入管道依赖。
     *
     * @param documentParsingService     百炼/DocMind 解析
     * @param semanticChunkingService    语义切片
     * @param metadataEnhancementService 元数据抽取
     * @param vectorStore                PG 向量存储
     */
    public EtlPipelineService(
            DocumentParsingService documentParsingService,
            SemanticChunkingService semanticChunkingService,
            MetadataEnhancementService metadataEnhancementService,
            VectorStore vectorStore) {
        this.documentParsingService = documentParsingService;
        this.semanticChunkingService = semanticChunkingService;
        this.metadataEnhancementService = metadataEnhancementService;
        this.vectorStore = vectorStore;
    }

    /**
     * 执行完整 ETL：上传文件经解析、切块、LLM 元数据，并最终量化为向量写入数据库。
     *
     * @param file       原始二进制
     * @param documentId 业务唯一键
     * @return 入库切片数量
     * @throws IOException 读取上传文件失败
     */
    public int runPipeline(MultipartFile file, String documentId) throws IOException {
        // 1) 文档解析为 Markdown 纯文本
        String markdown = documentParsingService.parseToMarkdown(file);
        // 2) 语义切块 + 10% overlap
        List<String> chunks = semanticChunkingService.chunkWithTenPercentOverlap(markdown);
        log.info("文档切分完成，共 {} 个 chunks", chunks.size());
        if (chunks.isEmpty()) {
            return 0;
        }
        // 3) 虚拟线程并发调用 LLM 抽取元数据并保持序号一致（Semaphore 限制并发避免远端限流）
        List<Document> documents = enrichAndBuildDocuments(documentId, chunks);
        // 4) 分批写入 PGVector：DashScope Embedding API 单次最多 25 条
        int batchSize = 25;
        for (int i = 0; i < documents.size(); i += batchSize) {
            List<Document> batch = documents.subList(i, Math.min(i + batchSize, documents.size()));
            vectorStore.add(batch);
        }
        return documents.size();
    }

    /**
     * 并发切片增强并装配 {@link Document} 列表。
     *
     * @param documentId 文档业务 ID
     * @param chunks     有序切片
     * @return Spring AI 文档列表
     */
    private List<Document> enrichAndBuildDocuments(String documentId, List<String> chunks) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Semaphore semaphore = new Semaphore(5);
            List<CompletableFuture<Document>> futures = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                final int idx = i;
                String chunk = chunks.get(i);
                CompletableFuture<Document> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        semaphore.acquire();
                        // 调用 DashScope Chat 完成结构化抽取
                        ExtractedMetadata meta = metadataEnhancementService.enhance(chunk);
                        Map<String, Object> metadata =
                                metadataEnhancementService.toMetadataMap(meta, documentId, idx);
                        return new Document(chunk, metadata);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("并发限流被中断", e);
                    } finally {
                        semaphore.release();
                    }
                }, executor);
                futures.add(future);
            }
            // 按提交顺序 join，确保 chunk_index 与原文顺序一致
            return futures.stream().map(CompletableFuture::join).toList();
        }
    }
}
