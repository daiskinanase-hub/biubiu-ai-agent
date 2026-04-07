package com.example.rag.exception;

/**
 * 外部服务调用相关业务异常。
 */
public class ExternalServiceException extends BusinessException {

    public ExternalServiceException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ExternalServiceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ExternalServiceException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public ExternalServiceException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static ExternalServiceException apiKeyInvalid() {
        return new ExternalServiceException(
                ErrorCode.API_KEY_INVALID,
                "API Key未配置或无效，请检查配置文件"
        );
    }

    public static ExternalServiceException dashScopeError(String operation, Throwable cause) {
        return new ExternalServiceException(
                ErrorCode.DASHSCOPE_ERROR,
                "阿里云 DashScope 服务调用失败 [" + operation + "]",
                cause
        );
    }

    public static ExternalServiceException networkTimeout(long timeoutMs) {
        return new ExternalServiceException(
                ErrorCode.NETWORK_TIMEOUT,
                String.format("网络请求超时 (%.1fs)", timeoutMs / 1000.0)
        );
    }

    public static ExternalServiceException quotaExceeded(String service) {
        return new ExternalServiceException(
                ErrorCode.QUOTA_EXCEEDED,
                "API 配额已用尽: " + service
        );
    }
}
