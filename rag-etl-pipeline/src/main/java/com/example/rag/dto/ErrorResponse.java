package com.example.rag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * 统一错误响应结构。
 * <p>
 * 遵循 RFC 7807 Problem Details 规范，提供一致的错误响应格式。
 * </p>
 *
 * <pre>
 * {
 *     "code": "ERR_2001",
 *     "message": "文档解析失败",
 *     "timestamp": "2024-01-15T10:30:00Z",
 *     "traceId": "abc123",
 *     "details": {
 *         "filename": "test.pdf",
 *         "documentId": "doc-456"
 *     }
 * }
 * </pre>
 *
 * @param code        错误码字符串，如 "ERR_2001"
 * @param message     人类可读的错误消息
 * @param timestamp   错误发生时间（ISO 8601格式）
 * @param traceId     请求追踪ID（用于日志关联）
 * @param path        请求路径
 * @param method      HTTP方法
 * @param details     额外的错误详情（可选）
 * @param stackTrace  堆栈信息（仅在调试模式启用）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @JsonProperty("code")
        String code,

        @JsonProperty("message")
        String message,

        @JsonProperty("timestamp")
        Instant timestamp,

        @JsonProperty("traceId")
        String traceId,

        @JsonProperty("path")
        String path,

        @JsonProperty("method")
        String method,

        @JsonProperty("details")
        Map<String, Object> details,

        @JsonProperty("stackTrace")
        String stackTrace
) {

    /**
     * 快速创建简单错误响应。
     *
     * @param code    错误码
     * @param message 错误消息
     * @return 错误响应
     */
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Instant.now(), null, null, null, null, null);
    }

    /**
     * 创建带追踪ID的错误响应。
     *
     * @param code    错误码
     * @param message 错误消息
     * @param traceId 追踪ID
     * @return 错误响应
     */
    public static ErrorResponse of(String code, String message, String traceId) {
        return new ErrorResponse(code, message, Instant.now(), traceId, null, null, null, null);
    }

    /**
     * 创建完整错误响应。
     *
     * @param code      错误码
     * @param message   错误消息
     * @param traceId   追踪ID
     * @param path      请求路径
     * @param method    HTTP方法
     * @param details   详情
     * @return 错误响应
     */
    public static ErrorResponse of(String code, String message, String traceId,
                                   String path, String method, Map<String, Object> details) {
        return new ErrorResponse(code, message, Instant.now(), traceId, path, method, details, null);
    }

    /**
     * 创建调试模式错误响应（包含堆栈）。
     *
     * @param code      错误码
     * @param message   错误消息
     * @param traceId   追踪ID
     * @param path      请求路径
     * @param method    HTTP方法
     * @param details   详情
     * @param throwable 原始异常
     * @return 错误响应
     */
    public static ErrorResponse debug(String code, String message, String traceId,
                                      String path, String method, Map<String, Object> details,
                                      Throwable throwable) {
        String stackTrace = throwable != null ? formatStackTrace(throwable) : null;
        return new ErrorResponse(code, message, Instant.now(), traceId, path, method, details, stackTrace);
    }

    /**
     * 格式化堆栈跟踪信息。
     */
    private static String formatStackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.getClass().getName()).append(": ").append(t.getMessage()).append("\n");
        for (StackTraceElement element : t.getStackTrace()) {
            if (sb.length() > 2000) {
                sb.append("... (truncated)");
                break;
            }
            sb.append("  at ").append(element).append("\n");
        }
        return sb.toString();
    }

    /**
     * 错误响应构建器。
     */
    public static class Builder {
        private String code;
        private String message;
        private String traceId;
        private String path;
        private String method;
        private Map<String, Object> details;

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        public Builder detail(String key, Object value) {
            if (this.details == null) {
                this.details = new java.util.LinkedHashMap<>();
            }
            this.details.put(key, value);
            return this;
        }

        public ErrorResponse build() {
            return new ErrorResponse(code, message, Instant.now(), traceId, path, method, details, null);
        }
    }
}
