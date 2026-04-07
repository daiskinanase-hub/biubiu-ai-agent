package com.example.rag.controller;

import com.example.rag.aop.PerformanceLog;
import com.example.rag.aop.PerformanceLogAspect;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查和监控接口。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final PerformanceLogAspect performanceLogAspect;

    public HealthController(PerformanceLogAspect performanceLogAspect) {
        this.performanceLogAspect = performanceLogAspect;
    }

    /**
     * 基础健康检查。
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toString());
        response.put("service", "rag-etl-pipeline");
        return ResponseEntity.ok(response);
    }

    /**
     * 详细健康信息。
     */
    @GetMapping("/health/detailed")
    @PerformanceLog(description = "健康检查", logArgs = false)
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toString());
        response.put("service", "rag-etl-pipeline");
        response.put("javaVersion", Runtime.version().toString());
        response.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        response.put("freeMemory", Runtime.getRuntime().freeMemory());
        response.put("totalMemory", Runtime.getRuntime().totalMemory());
        return ResponseEntity.ok(response);
    }

    /**
     * 获取方法调用统计。
     */
    @GetMapping("/metrics/method-stats")
    @PerformanceLog(description = "获取方法统计", logArgs = false)
    public ResponseEntity<Map<String, Object>> methodStats() {
        Map<String, PerformanceLogAspect.MethodStats> stats = performanceLogAspect.getAllStats();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("methodCount", stats.size());

        Map<String, Map<String, Object>> methodDetails = new LinkedHashMap<>();
        stats.forEach((method, stat) -> {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("totalCalls", stat.getTotalCalls());
            detail.put("errorCalls", stat.getErrorCalls());
            detail.put("avgDuration", stat.getAvgDuration());
            detail.put("maxDuration", stat.getMaxDuration());
            methodDetails.put(method, detail);
        });
        response.put("methods", methodDetails);

        return ResponseEntity.ok(response);
    }
}
