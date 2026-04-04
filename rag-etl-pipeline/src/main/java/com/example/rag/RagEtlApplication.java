package com.example.rag;

import com.example.rag.config.DashScopeDataCenterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * RAG ETL 管道应用入口：文档解析、语义切片、元数据增强与向量入库。
 */
@SpringBootApplication
@EnableConfigurationProperties(DashScopeDataCenterProperties.class)
public class RagEtlApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(RagEtlApplication.class, args);
    }
}
