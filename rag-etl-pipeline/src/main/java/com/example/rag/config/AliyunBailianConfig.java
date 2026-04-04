package com.example.rag.config;

import com.aliyuncs.IAcsClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 加载阿里云 AK/SK，并暴露 {@link IAcsClient}（便于后续扩展 OpenAPI/RPC 调用）。
 * 文档解析主链路使用 DashScope DataCenter REST + API Key；AK/SK 可为空，但 RPC 调用前需补齐凭证。
 */
@Configuration
@EnableConfigurationProperties(AliyunBailianProperties.class)
public class AliyunBailianConfig {

    // 暂不启用 AK/SK 通用客户端扩展，后续需要接入 RPC/OpenAPI 时再打开。
    //
    // /**
    //  * 基于配置创建阿里云通用客户端。
    //  *
    //  * @param properties 包含 region、AccessKeyId、AccessKeySecret 的配置
    //  * @return IAcsClient 实例；若未配置 AK/SK，则使用空字符串占位（仅用于启动，不自作 RPC 调用）
    //  */
    // @Bean(destroyMethod = "shutdown")
    // public IAcsClient aliyunAcsClient(AliyunBailianProperties properties) {
    //     String region = StringUtils.hasText(properties.regionId()) ? properties.regionId() : "cn-hangzhou";
    //     String ak = properties.accessKeyId() != null ? properties.accessKeyId() : "";
    //     String sk = properties.accessKeySecret() != null ? properties.accessKeySecret() : "";
    //     // 组装客户端 Profile，供后续基于 aliyun-java-sdk-core 的 OpenAPI 封装使用
    //     IClientProfile profile = DefaultProfile.getProfile(region, ak, sk);
    //     return new DefaultAcsClient(profile);
    // }
}
