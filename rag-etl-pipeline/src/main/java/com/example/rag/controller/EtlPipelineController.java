package com.example.rag.controller;

import com.example.rag.dto.EtlProcessResponse;
import com.example.rag.service.EtlPipelineService;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 触发企业 RAG ETL 管道的 REST 接口。
 */
@RestController
@RequestMapping("/api/v1/etl")
public class EtlPipelineController {

    private final EtlPipelineService etlPipelineService;

    /**
     * 注入编排服务。
     *
     * @param etlPipelineService 主编排服务
     */
    public EtlPipelineController(EtlPipelineService etlPipelineService) {
        this.etlPipelineService = etlPipelineService;
    }

    /**
     * 接收原始文档与业务 ID，运行解析→切块→元数据→向量入库。
     *
     * @param file       multipart 文件
     * @param documentId 业务唯一标识
     * @return 执行统计
     * @throws IOException 读文件失败
     */
    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EtlProcessResponse process(
            @RequestPart("file") MultipartFile file,
            @RequestParam("documentId") String documentId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file 不能为空");
        }
        if (!StringUtils.hasText(documentId)) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        // 获取原始文件名
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        int n = etlPipelineService.runPipeline(file, documentId.trim());
        return new EtlProcessResponse(
                "success",
                documentId,
                originalFilename,
                n,
                "Documents parsed, chunked, enriched with metadata, and stored in PGVector successfully.");
    }
}
