package com.example.rag.exception;

/**
 * 验证异常，用于参数校验失败等场景。
 */
public class ValidationException extends BusinessException {

    public ValidationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ValidationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ValidationException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public static ValidationException missingParameter(String paramName) {
        return new ValidationException(
                ErrorCode.MISSING_PARAMETER,
                "缺少必需参数: " + paramName
        );
    }

    public static ValidationException invalidParameter(String paramName, String reason) {
        return new ValidationException(
                ErrorCode.INVALID_PARAMETER,
                String.format("参数 '%s' 值无效: %s", paramName, reason)
        );
    }

    public static ValidationException fileUploadError(String filename, Throwable cause) {
        return new ValidationException(
                ErrorCode.FILE_UPLOAD_ERROR,
                "文件上传失败: " + filename
        );
    }
}
