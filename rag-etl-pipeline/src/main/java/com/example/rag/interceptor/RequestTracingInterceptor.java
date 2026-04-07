package com.example.rag.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.UUID;

/**
 * 请求追踪拦截器 - 为每个请求分配唯一追踪ID。
 * <p>
 * 功能：
 * <ul>
 *   <li>生成/接收请求追踪ID</li>
 *   <li>注入MDC用于日志关联</li>
 *   <li>添加响应头供前端排查</li>
 *   <li>记录请求处理耗时</li>
 * </ul>
 * </p>
 */
@Component
public class RequestTracingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestTracingInterceptor.class);

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String REQUEST_START_TIME_KEY = "requestStartTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 获取或生成追踪ID
        String traceId = getOrGenerateTraceId(request);

        // 注入MDC
        MDC.put(TRACE_ID_MDC_KEY, traceId);

        // 记录请求开始时间
        request.setAttribute(REQUEST_START_TIME_KEY, System.currentTimeMillis());

        // 添加响应头
        response.setHeader(TRACE_ID_HEADER, traceId);

        // 记录请求入口日志
        log.info("[请求入口] traceId={} method={} uri={} query={} remoteAddr={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                getClientIp(request));

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                          Object handler, ModelAndView modelAndView) {
        // 控制器方法执行后（视图渲染前）的处理
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        // 请求完成后
        long startTime = (Long) request.getAttribute(REQUEST_START_TIME_KEY);
        long duration = System.currentTimeMillis() - startTime;
        String traceId = MDC.get(TRACE_ID_MDC_KEY);

        // 记录请求完成日志
        if (ex != null) {
            log.error("[请求完成] traceId={} status={} duration={}ms 异常",
                    traceId, response.getStatus(), duration);
        } else {
            log.info("[请求完成] traceId={} status={} duration={}ms",
                    traceId, response.getStatus(), duration);
        }

        // 清理MDC
        MDC.clear();
    }

    /**
     * 获取或生成追踪ID。
     * 优先使用请求头中的ID，支持分布式追踪。
     */
    private String getOrGenerateTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = generateTraceId();
        }
        return traceId;
    }

    /**
     * 生成唯一追踪ID。
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 获取客户端真实IP地址。
     * 支持代理场景下的IP获取。
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (isValidIp(ip)) {
                // 多个IP时取第一个
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * 验证IP是否有效。
     */
    private boolean isValidIp(String ip) {
        return ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip);
    }
}
