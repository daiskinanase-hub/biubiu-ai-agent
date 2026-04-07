package com.example.rag.exception;

/**
 * 业务错误码枚举定义。
 * <p>
 * 遵循行业标准错误码规范：
 * <ul>
 *   <li>1xxx - 系统级错误</li>
 *   <li>2xxx - 文档处理错误</li>
 *   <li>3xxx - RAG查询错误</li>
 *   <li>4xxx - 外部服务错误</li>
 *   <li>5xxx - 验证/参数错误</li>
 * </ul>
 * </p>
 */
public enum ErrorCode {

    // ========== 1xxx 系统级错误 ==========
    /**
     * 系统内部未知错误
     */
    SYSTEM_ERROR(1000, "系统内部错误，请稍后重试"),

    /**
     * 服务暂不可用（维护/过载）
     */
    SERVICE_UNAVAILABLE(1001, "服务暂不可用，请稍后重试"),

    /**
     * 数据库连接失败
     */
    DATABASE_ERROR(1002, "数据库操作失败"),

    // ========== 2xxx 文档处理错误 ==========
    /**
     * 文档解析失败
     */
    DOCUMENT_PARSE_ERROR(2001, "文档解析失败"),

    /**
     * 文档内容为空
     */
    DOCUMENT_EMPTY(2002, "文档内容为空"),

    /**
     * 不支持的文档格式
     */
    UNSUPPORTED_FORMAT(2003, "不支持的文档格式"),

    /**
     * 文档大小超限
     */
    DOCUMENT_SIZE_EXCEEDED(2004, "文档大小超过限制"),

    /**
     * 向量存储失败
     */
    VECTOR_STORE_ERROR(2005, "向量存储失败"),

    // ========== 3xxx RAG查询错误 ==========
    /**
     * 向量检索失败
     */
    VECTOR_SEARCH_ERROR(3001, "向量检索失败"),

    /**
     * LLM生成失败
     */
    LLM_GENERATION_ERROR(3002, "AI生成失败"),

    /**
     * 重排服务调用失败
     */
    RERANK_ERROR(3003, "文档重排失败"),

    /**
     * 文档未找到
     */
    DOCUMENT_NOT_FOUND(3004, "指定的文档不存在"),

    /**
     * 无相关检索结果
     */
    NO_RELEVANT_RESULTS(3005, "未找到相关内容"),

    // ========== 4xxx 外部服务错误 ==========
    /**
     * API Key未配置或无效
     */
    API_KEY_INVALID(4001, "API配置错误"),

    /**
     * DashScope服务调用失败
     */
    DASHSCOPE_ERROR(4002, "阿里云服务调用失败"),

    /**
     * 网络请求超时
     */
    NETWORK_TIMEOUT(4003, "网络请求超时"),

    /**
     * 配额超限
     */
    QUOTA_EXCEEDED(4004, "API配额已用尽"),

    // ========== 5xxx 验证/参数错误 ==========
    /**
     * 必填参数缺失
     */
    MISSING_PARAMETER(5001, "缺少必要参数"),

    /**
     * 参数格式错误
     */
    INVALID_PARAMETER(5002, "参数格式错误"),

    /**
     * 文件上传失败
     */
    FILE_UPLOAD_ERROR(5003, "文件上传失败");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取错误码数字。
     *
     * @return 错误码
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取默认错误消息。
     *
     * @return 默认消息
     */
    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * 获取错误码字符串格式。
     *
     * @return 格式如 "ERR_2001"
     */
    public String getCodeString() {
        return String.format("ERR_%04d", code);
    }
}
