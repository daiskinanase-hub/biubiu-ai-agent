package com.example.rag.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * 操作日志切面 - 记录业务操作的详细信息。
 * <p>
 * 自动记录：
 * <ul>
 *   <li>操作开始/结束</li>
 *   <li>操作成功/失败</li>
 *   <li>关键业务数据变更</li>
 * </ul>
 * </p>
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);
    private static final String TRACE_ID_KEY = "traceId";
    private static final String OPERATION_KEY = "operation";

    // ==================== Pointcut 定义 ====================

    /**
     * 切入点：所有Controller的public方法
     */
    @Pointcut("execution(public * com.example.rag.controller..*.*(..))")
    public void controllerMethods() {}

    /**
     * 切入点：ETL处理相关方法
     */
    @Pointcut("execution(* com.example.rag.service.EtlPipelineService.runPipeline(..))")
    public void etlProcessingMethods() {}

    /**
     * 切入点：RAG查询方法
     */
    @Pointcut("execution(* com.example.rag.service.RagQueryService.query(..))")
    public void ragQueryMethods() {}

    // ==================== 通知 ====================

    /**
     * ETL处理前记录参数
     */
    @Before("etlProcessingMethods()")
    public void beforeEtlProcessing(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        String traceId = MDC.get(TRACE_ID_KEY);

        String documentId = args.length > 1 ? String.valueOf(args[1]) : "unknown";
        String filename = extractFilename(args[0]);

        log.info("[ETL开始] traceId={} documentId={} filename={}",
                traceId, documentId, filename);
    }

    /**
     * ETL处理后记录结果
     */
    @AfterReturning(pointcut = "etlProcessingMethods()", returning = "result")
    public void afterEtlProcessing(JoinPoint joinPoint, Object result) {
        String traceId = MDC.get(TRACE_ID_KEY);
        int chunkCount = result instanceof Integer ? (Integer) result : 0;

        log.info("[ETL完成] traceId={} chunkCount={} status=SUCCESS",
                traceId, chunkCount);
    }

    /**
     * ETL异常处理
     */
    @AfterThrowing(pointcut = "etlProcessingMethods()", throwing = "ex")
    public void onEtlException(JoinPoint joinPoint, Throwable ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        Object[] args = joinPoint.getArgs();
        String documentId = args.length > 1 ? String.valueOf(args[1]) : "unknown";

        log.error("[ETL异常] traceId={} documentId={} error={} message={}",
                traceId, documentId, ex.getClass().getSimpleName(), ex.getMessage());
    }

    /**
     * RAG查询前记录
     */
    @Before("ragQueryMethods()")
    public void beforeRagQuery(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        String traceId = MDC.get(TRACE_ID_KEY);

        if (args.length > 0 && args[0] != null) {
            String requestStr = args[0].toString();
            log.info("[RAG查询开始] traceId={} request={}",
                    traceId, truncateForLog(requestStr, 200));
        }
    }

    /**
     * RAG查询后记录
     */
    @AfterReturning(pointcut = "ragQueryMethods()", returning = "result")
    public void afterRagQuery(JoinPoint joinPoint, Object result) {
        String traceId = MDC.get(TRACE_ID_KEY);

        if (result != null) {
            String resultStr = result.toString();
            log.info("[RAG查询完成] traceId={} result={} status=SUCCESS",
                    traceId, truncateForLog(resultStr, 100));
        }
    }

    /**
     * RAG查询异常
     */
    @AfterThrowing(pointcut = "ragQueryMethods()", throwing = "ex")
    public void onRagQueryException(JoinPoint joinPoint, Throwable ex) {
        String traceId = MDC.get(TRACE_ID_KEY);

        log.error("[RAG查询异常] traceId={} error={} message={}",
                traceId, ex.getClass().getSimpleName(), ex.getMessage());
    }

    // ==================== 辅助方法 ====================

    /**
     * 从文件参数中提取文件名。
     */
    private String extractFilename(Object fileObj) {
        if (fileObj == null) {
            return "null";
        }
        try {
            var field = fileObj.getClass().getDeclaredField("originalFilename");
            field.setAccessible(true);
            Object value = field.get(fileObj);
            return value != null ? value.toString() : "unknown";
        } catch (Exception e) {
            return fileObj.getClass().getSimpleName();
        }
    }

    /**
     * 截断日志内容。
     */
    private String truncateForLog(String str, int maxLength) {
        if (str == null) {
            return "null";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...(truncated)";
    }
}
