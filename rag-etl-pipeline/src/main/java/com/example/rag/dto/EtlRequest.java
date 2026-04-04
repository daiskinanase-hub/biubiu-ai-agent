package com.example.rag.dto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.multipart.MultipartFile;

/**
 * ETL 请求视图（可与表单字段对应，便于扩展为 JSON 体）。
 *
 * @param documentId 业务文档唯一标识
 * @param file       原始文档二进制（multipart 场景由控制器单独接收）
 */
public record EtlRequest(
        @NotBlank String documentId,
        MultipartFile file
) {
}
