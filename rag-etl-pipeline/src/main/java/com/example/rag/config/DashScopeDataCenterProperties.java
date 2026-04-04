package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DashScope DataCenter（类目文件解析）相关配置。
 *
 * @param baseUrl            API 根地址，默认 https://dashscope.aliyuncs.com
 * @param categoryId        类目 ID，默认 default
 * @param workspaceId       业务空间 ID，默认空串表示默认空间
 * @param parser            解析器标识，与 ResultType.DASHSCOPE_DOCMIND 一致
 * @param pollIntervalSeconds 轮询解析状态间隔秒数
 * @param maxWaitSeconds    最大等待解析完成时间
 */
@ConfigurationProperties(prefix = "rag.dashscope")
public record DashScopeDataCenterProperties(
        String baseUrl,
        String categoryId,
        String workspaceId,
        String parser,
        int pollIntervalSeconds,
        int maxWaitSeconds
) {
}
