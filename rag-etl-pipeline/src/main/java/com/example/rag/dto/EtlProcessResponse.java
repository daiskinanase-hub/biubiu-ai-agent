package com.example.rag.dto;

/**
 * ETL 管道执行结果响应体。
 *
 * @param status          调度状态，如 success
 * @param documentId      业务文档 ID
 * @param documentName    文档原始文件名
 * @param chunksProcessed 写入向量库的切片数量
 * @param message         人类可读说明
 */
public record EtlProcessResponse(
        String status,
        String documentId,
        String documentName,
        int chunksProcessed,
        String message
) {
}
