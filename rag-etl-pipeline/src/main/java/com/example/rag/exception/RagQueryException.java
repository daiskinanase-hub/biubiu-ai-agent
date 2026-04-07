package com.example.rag.exception;

/**
 * RAG查询相关业务异常。
 */
public class RagQueryException extends BusinessException {

    public RagQueryException(ErrorCode errorCode) {
        super(errorCode);
    }

    public RagQueryException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public RagQueryException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public RagQueryException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static RagQueryException vectorSearchError(Throwable cause) {
        return new RagQueryException(ErrorCode.VECTOR_SEARCH_ERROR, cause);
    }

    public static RagQueryException generationError(Throwable cause) {
        return new RagQueryException(ErrorCode.LLM_GENERATION_ERROR, cause);
    }

    public static RagQueryException rerankError(Throwable cause) {
        return new RagQueryException(ErrorCode.RERANK_ERROR, cause);
    }

    public static RagQueryException noRelevantResults() {
        return new RagQueryException(ErrorCode.NO_RELEVANT_RESULTS);
    }

    public static RagQueryException serviceUnavailable() {
        return new RagQueryException(ErrorCode.SERVICE_UNAVAILABLE);
    }
}
