package com.example.rag.exception;

import com.example.rag.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器 - 增强版。
 * <p>
 * 功能：
 * <ul>
 *   <li>统一错误响应格式（RFC 7807）</li>
 *   <li>业务异常分类处理</li>
 *   <li>请求追踪ID注入</li>
 *   <li>详细日志记录</li>
 *   <li>区分生产/调试模式</li>
 * </ul>
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TRACE_ID_KEY = "traceId";

    @Value("${app.debug-mode:false}")
    private boolean debugMode;

    // ==================== 业务异常处理 ====================

    /**
     * 处理业务异常。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {

        String traceId = getTraceId();
        HttpStatus status = determineHttpStatus(ex.getErrorCode());
        String path = request.getRequestURI();
        String httpMethod = request.getMethod();

        // 获取额外上下文信息
        Map<String, Object> details = extractExceptionDetails(ex);

        if (debugMode) {
            log.error("[业务异常] traceId={} code={} message={} path={} method={}",
                    traceId, ex.getErrorCode().getCodeString(), ex.getMessage(), path, httpMethod, ex);
            return ResponseEntity.status(status).body(
                    ErrorResponse.debug(
                            ex.getErrorCode().getCodeString(),
                            ex.getMessage(),
                            traceId,
                            path,
                            httpMethod,
                            details,
                            ex
                    )
            );
        } else {
            log.warn("[业务异常] traceId={} code={} message={} path={} method={}",
                    traceId, ex.getErrorCode().getCodeString(), ex.getMessage(), path, httpMethod);
            return ResponseEntity.status(status).body(
                    ErrorResponse.of(
                            ex.getErrorCode().getCodeString(),
                            ex.getMessage(),
                            traceId
                    )
            );
        }
    }

    /**
     * 处理验证异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String traceId = getTraceId();
        String path = request.getRequestURI();

        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        e -> e.getField(),
                        e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "invalid"
                ));

        Map<String, Object> details = Map.of("errors", errors);

        log.warn("[验证异常] traceId={} path={} errors={}",
                traceId, path, errors);

        return ResponseEntity.badRequest().body(
                new ErrorResponse(
                        ErrorCode.INVALID_PARAMETER.getCodeString(),
                        "请求参数验证失败",
                        Instant.now(),
                        traceId,
                        path,
                        request.getMethod(),
                        details,
                        null
                )
        );
    }

    /**
     * 处理文件上传大小超限。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {

        String traceId = getTraceId();
        log.warn("[文件超限] traceId={} message={}", traceId, ex.getMessage());

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                ErrorResponse.of(
                        ErrorCode.DOCUMENT_SIZE_EXCEEDED.getCodeString(),
                        ErrorCode.DOCUMENT_SIZE_EXCEEDED.getDefaultMessage(),
                        traceId
                )
        );
    }

    /**
     * 处理IllegalArgumentException。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {

        String traceId = getTraceId();
        String path = request.getRequestURI();

        log.warn("[参数异常] traceId={} path={} message={}", traceId, path, ex.getMessage());

        return ResponseEntity.badRequest().body(
                ErrorResponse.of(
                        ErrorCode.INVALID_PARAMETER.getCodeString(),
                        ex.getMessage(),
                        traceId
                )
        );
    }

    // ==================== 通用异常处理 ====================

    /**
     * 处理所有未捕获的异常。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception ex, HttpServletRequest request) {

        String traceId = getTraceId();
        String path = request.getRequestURI();
        String httpMethod = request.getMethod();

        log.error("[系统异常] traceId={} path={} method={} type={} message={}",
                traceId, path, httpMethod, ex.getClass().getSimpleName(), ex.getMessage(), ex);

        if (debugMode) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ErrorResponse.debug(
                            ErrorCode.SYSTEM_ERROR.getCodeString(),
                            "系统内部错误",
                            traceId,
                            path,
                            httpMethod,
                            Map.of("rootCause", getRootCause(ex)),
                            ex
                    )
            );
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ErrorResponse.of(
                            ErrorCode.SYSTEM_ERROR.getCodeString(),
                            ErrorCode.SYSTEM_ERROR.getDefaultMessage(),
                            traceId
                    )
            );
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 根据错误码确定HTTP状态码。
     */
    private HttpStatus determineHttpStatus(ErrorCode errorCode) {
        if (errorCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return switch (errorCode) {
            case DOCUMENT_NOT_FOUND, NO_RELEVANT_RESULTS -> HttpStatus.NOT_FOUND;
            case MISSING_PARAMETER, INVALID_PARAMETER, FILE_UPLOAD_ERROR -> HttpStatus.BAD_REQUEST;
            case QUOTA_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case DOCUMENT_SIZE_EXCEEDED -> HttpStatus.PAYLOAD_TOO_LARGE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * 从异常中提取详情信息。
     */
    private Map<String, Object> extractExceptionDetails(BusinessException ex) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        ErrorCode code = ex.getErrorCode();
        if (code != null) {
            details.put("errorCode", code.getCode());
        }
        String msg = ex.getMessage();
        if (msg != null && !msg.equals(code.getDefaultMessage())) {
            details.put("details", msg);
        }
        return details.isEmpty() ? null : details;
    }

    /**
     * 获取根因异常信息。
     */
    private String getRootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    /**
     * 获取追踪ID（从MDC或生成新ID）。
     */
    private String getTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return traceId != null ? traceId : "N/A";
    }
}
