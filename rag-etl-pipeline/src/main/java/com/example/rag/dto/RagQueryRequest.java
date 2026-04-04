package com.example.rag.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * RAG 查询请求体。
 *
 * @param documentId 文档唯一标识
 * @param prompt     用户原始提示词
 */
public record RagQueryRequest(
        @NotBlank String documentId,
        @NotBlank String prompt
) {
}
