package com.example.rag.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 性能日志切面 - AOP方式记录方法执行性能。
 * <p>
 * 功能：
 * <ul>
 *   <li>记录方法执行时间</li>
 *   <li>识别慢查询（可配置阈值）</li>
 *   <li>统计方法调用频次</li>
 *   <li>记录方法入参（脱敏处理）</li>
 * </ul>
 * </p>
 */
@Aspect
@Component
public class PerformanceLogAspect {

    private static final Logger log = LoggerFactory.getLogger(PerformanceLogAspect.class);
    private static final String TRACE_ID_KEY = "traceId";

    // 慢方法阈值配置（毫秒）
    private static final long SLOW_METHOD_THRESHOLD_MS = 1000;

    // 调用统计
    private final ConcurrentMap<String, MethodStats> methodStats = new ConcurrentHashMap<>();

    // ==================== Pointcut 定义 ====================

    /**
     * 切入点：Service层的所有public方法
     */
    @Pointcut("execution(public * com.example.rag.service..*.*(..))")
    public void serviceLayer() {}

    /**
     * 切入点：Controller层的所有public方法
     */
    @Pointcut("execution(public * com.example.rag.controller..*.*(..))")
    public void controllerLayer() {}

    /**
     * 切入点：外部服务调用（如RestTemplate、WebClient等）
     */
    @Pointcut("execution(* org.springframework.web.client..*(..))")
    public void externalServiceCall() {}

    /**
     * 切入点：数据库操作
     */
    @Pointcut("execution(* org.springframework.jdbc.core..*(..))")
    public void databaseOperation() {}

    /**
     * 切入点：自定义性能日志注解
     */
    @Pointcut("@annotation(com.example.rag.aop.PerformanceLog)")
    public void performanceLogAnnotation() {}

    // ==================== 环绕通知 ====================

    /**
     * Service层性能日志
     */
    @Around("serviceLayer() && !externalServiceCall() && !databaseOperation()")
    public Object logServicePerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return logPerformance(joinPoint, "SVC");
    }

    /**
     * Controller层性能日志
     */
    @Around("controllerLayer() && !externalServiceCall()")
    public Object logControllerPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return logPerformance(joinPoint, "API");
    }

    /**
     * 自定义注解标记的方法
     */
    @Around("performanceLogAnnotation()")
    public Object logAnnotatedPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return logPerformance(joinPoint, "ANN");
    }

    // ==================== 核心日志逻辑 ====================

    private Object logPerformance(ProceedingJoinPoint joinPoint, String layer) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        String fullMethodName = className + "." + methodName;
        String traceId = MDC.get(TRACE_ID_KEY);

        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable exception = null;

        try {
            // 执行目标方法
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            exception = t;
            throw t;
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // 更新统计信息
            updateStats(fullMethodName, duration, exception != null);

            // 记录日志
            logMethodExecution(layer, fullMethodName, traceId, duration, exception,
                    joinPoint.getArgs(), result);
        }
    }

    /**
     * 记录方法执行日志。
     */
    private void logMethodExecution(String layer, String methodName, String traceId,
                                   long duration, Throwable exception,
                                   Object[] args, Object result) {
        // 构建日志前缀
        String logPrefix = String.format("[%s] %s | traceId=%s | %s | duration=%dms",
                layer, traceId, traceId, methodName, duration);

        if (exception != null) {
            // 异常情况
            log.error("{} | 状态=异常 | error={}", logPrefix, exception.getMessage());
        } else if (duration > SLOW_METHOD_THRESHOLD_MS) {
            // 慢方法警告
            log.warn("{} | 状态=慢方法 | threshold={}ms | args={}",
                    logPrefix, SLOW_METHOD_THRESHOLD_MS, sanitizeArgs(args));
        } else {
            // 正常情况（DEBUG级别）
            if (log.isDebugEnabled()) {
                log.debug("{} | 状态=正常 | args={}", logPrefix, sanitizeArgs(args));
            }
        }
    }

    /**
     * 更新方法统计信息。
     */
    private void updateStats(String methodName, long duration, boolean hasError) {
        methodStats.computeIfAbsent(methodName, k -> new MethodStats())
                .record(duration, hasError);
    }

    /**
     * 参数脱敏处理 - 移除敏感信息。
     */
    private Object sanitizeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }

        return Arrays.stream(args)
                .map(this::sanitizeObject)
                .toList()
                .toString();
    }

    /**
     * 单个对象脱敏。
     */
    private Object sanitizeObject(Object obj) {
        if (obj == null) {
            return null;
        }

        // 常见敏感字段
        if (obj instanceof String s) {
            if (s.length() > 100) {
                return s.substring(0, 100) + "...(truncated)";
            }
            return s;
        }

        // 文件对象只显示文件名和大小
        if (obj.getClass().getName().contains("MultipartFile")) {
            try {
                return String.format("MultipartFile[name=%s, size=%s]",
                        getFieldValue(obj, "originalFilename"),
                        getFieldValue(obj, "size"));
            } catch (Exception e) {
                return obj.getClass().getSimpleName();
            }
        }

        // 字节数组显示长度
        if (obj instanceof byte[]) {
            return String.format("byte[%d]", ((byte[]) obj).length);
        }

        // 集合/数组显示大小
        if (obj instanceof java.util.Collection c) {
            return String.format("%s[size=%d]", obj.getClass().getSimpleName(), c.size());
        }

        return obj.getClass().getSimpleName();
    }

    /**
     * 通过反射获取对象字段值。
     */
    private Object getFieldValue(Object obj, String fieldName) {
        try {
            var field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ==================== 统计信息内部类 ====================

    /**
     * 方法统计信息。
     */
    public static class MethodStats {
        private final AtomicLong totalCalls = new AtomicLong(0);
        private final AtomicLong errorCalls = new AtomicLong(0);
        private final AtomicLong totalDuration = new AtomicLong(0);
        private final AtomicLong maxDuration = new AtomicLong(0);
        private volatile long lastResetTime = System.currentTimeMillis();

        public void record(long duration, boolean hasError) {
            totalCalls.incrementAndGet();
            totalDuration.addAndGet(duration);
            maxDuration.updateAndGet(current -> Math.max(current, duration));
            if (hasError) {
                errorCalls.incrementAndGet();
            }
        }

        public long getTotalCalls() { return totalCalls.get(); }
        public long getErrorCalls() { return errorCalls.get(); }
        public long getAvgDuration() {
            long total = totalCalls.get();
            return total > 0 ? totalDuration.get() / total : 0;
        }
        public long getMaxDuration() { return maxDuration.get(); }
    }

    // ==================== 公共API ====================

    /**
     * 获取所有方法的统计信息。
     */
    public ConcurrentMap<String, MethodStats> getAllStats() {
        return new ConcurrentHashMap<>(methodStats);
    }

    /**
     * 获取指定方法的统计信息。
     */
    public MethodStats getStats(String methodName) {
        return methodStats.get(methodName);
    }

    /**
     * 重置所有统计信息。
     */
    public void resetStats() {
        methodStats.clear();
    }
}
