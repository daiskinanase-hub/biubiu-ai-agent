package com.example.rag.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 性能日志注解 - 标记需要记录性能日志的方法。
 * <p>
 * 使用方式：
 * <pre>
 * {@code
 * @PerformanceLog(description = "用户登录")
 * public User login(String username, String password) {
 *     // ...
 * }
 * }
 * </pre>
 * </p>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PerformanceLog {

    /**
     * 方法描述。
     */
    String description() default "";

    /**
     * 是否记录入参。
     */
    boolean logArgs() default true;

    /**
     * 是否记录返回值。
     */
    boolean logResult() default false;

    /**
     * 慢方法阈值（毫秒），超过此值记录警告日志。
     */
    long slowThreshold() default 500;
}
