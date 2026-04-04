package com.example.rag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * RAG 查询响应体（非流式）。
 *
 * @param answer   AI 生成的回答
 * @param docCount 命中的文档切片数量
 * @param error    错误信息（正常时为 null）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RagQueryResponse(
        String answer,
        Integer docCount,
        String error
) {
}
