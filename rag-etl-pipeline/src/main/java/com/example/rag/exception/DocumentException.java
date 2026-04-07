package com.example.rag.exception;

/**
 * 文档处理相关业务异常。
 */
public class DocumentException extends BusinessException {

    public DocumentException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DocumentException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public DocumentException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public DocumentException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static DocumentException parseError(String filename, Throwable cause) {
        return new DocumentException(
                ErrorCode.DOCUMENT_PARSE_ERROR,
                "文档解析失败: " + filename,
                cause
        );
    }

    public static DocumentException emptyDocument(String documentId) {
        return new DocumentException(
                ErrorCode.DOCUMENT_EMPTY,
                "文档内容为空: " + documentId
        );
    }

    public static DocumentException unsupportedFormat(String filename, String format) {
        return new DocumentException(
                ErrorCode.UNSUPPORTED_FORMAT,
                String.format("不支持的文档格式 [%s]: %s", format, filename)
        );
    }

    public static DocumentException sizeExceeded(String filename, long size, long maxSize) {
        return new DocumentException(
                ErrorCode.DOCUMENT_SIZE_EXCEEDED,
                String.format("文档大小超限 [%d > %d]: %s", size, maxSize, filename)
        );
    }

    public static DocumentException notFound(String documentId) {
        return new DocumentException(
                ErrorCode.DOCUMENT_NOT_FOUND,
                "文档不存在: " + documentId
        );
    }
}
