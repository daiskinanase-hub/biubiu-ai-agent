package com.example.rag.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 业务异常基类，支持错误码、错误消息和上下文信息。
 * <p>
 * 特性：
 * <ul>
 *   <li>支持错误码枚举</li>
 *   <li>支持自定义错误消息</li>
 *   <li>支持链式异常</li>
 *   <li>自动记录结构化日志</li>
 * </ul>
 * </p>
 */
public class BusinessException extends RuntimeException {

    private static final Logger log = LoggerFactory.getLogger(BusinessException.class);

    private final ErrorCode errorCode;
    private final String customMessage;
    private final Object[] context;

    /**
     * 使用错误码创建业务异常。
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.customMessage = null;
        this.context = null;
        logBusinessException();
    }

    /**
     * 使用错误码和自定义消息创建业务异常。
     *
     * @param errorCode 错误码枚举
     * @param message    自定义错误消息
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.customMessage = message;
        this.context = null;
        logBusinessException();
    }

    /**
     * 使用错误码和链式异常创建业务异常。
     *
     * @param errorCode 错误码枚举
     * @param cause      原始异常
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
        this.customMessage = null;
        this.context = null;
        logBusinessException();
    }

    /**
     * 使用错误码、自定义消息和链式异常创建业务异常。
     *
     * @param errorCode 错误码枚举
     * @param message    自定义错误消息
     * @param cause      原始异常
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.customMessage = message;
        this.context = null;
        logBusinessException();
    }

    /**
     * 使用错误码和上下文信息创建业务异常。
     *
     * @param errorCode 错误码枚举
     * @param context    上下文信息（用于日志记录）
     */
    public BusinessException(ErrorCode errorCode, Object... context) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.customMessage = null;
        this.context = context;
        logBusinessException();
    }

    /**
     * 记录业务异常的结构化日志。
     */
    private void logBusinessException() {
        if (context != null && context.length > 0) {
            log.warn("[业务异常] code={} message={} context={}",
                    errorCode.getCodeString(), getMessage(), java.util.Arrays.toString(context));
        } else {
            log.warn("[业务异常] code={} message={}",
                    errorCode.getCodeString(), getMessage());
        }
    }

    /**
     * 获取错误码。
     *
     * @return 错误码枚举
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 获取错误消息（优先返回自定义消息）。
     *
     * @return 错误消息
     */
    @Override
    public String getMessage() {
        return customMessage != null ? customMessage : errorCode.getDefaultMessage();
    }

    /**
     * 获取原始消息（忽略自定义消息）。
     *
     * @return 错误码默认消息
     */
    public String getOriginalMessage() {
        return errorCode.getDefaultMessage();
    }
}
