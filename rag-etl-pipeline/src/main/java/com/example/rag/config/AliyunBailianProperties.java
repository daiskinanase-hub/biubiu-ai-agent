package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云访问凭证配置，用于 {@link com.aliyuncs.profile.DefaultProfile} 与 {@link com.aliyuncs.DefaultAcsClient}。
 *
 * @param regionId         地域，如 cn-hangzhou
 * @param accessKeyId     AccessKey ID
 * @param accessKeySecret AccessKey Secret
 */
@ConfigurationProperties(prefix = "aliyun.bailian")
public record AliyunBailianProperties(
        String regionId,
        String accessKeyId,
        String accessKeySecret
) {
}
