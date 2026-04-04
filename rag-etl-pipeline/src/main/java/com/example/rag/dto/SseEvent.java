package com.example.rag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SSE 统一事件包装体。
 *
 * @param type    事件类型：chunk / error / done
 * @param content 内容片段（done 时可为 null）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SseEvent(
        String type,
        String content
) {
}
