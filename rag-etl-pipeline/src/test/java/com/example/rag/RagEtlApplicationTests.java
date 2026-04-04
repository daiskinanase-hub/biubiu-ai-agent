package com.example.rag;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * 应用上下文集成测试：验证依赖与自动装配是否完整。
 * <p>真实 ETL（DashScope、PG）需在配置 API Key、数据库后手动或另建集成测试类验证。</p>
 */
@SpringBootTest
class RagEtlApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 校验 Spring 容器成功启动且根上下文非空。
     */
    @Test
    void contextLoads() {
        Assertions.assertNotNull(applicationContext);
        Assertions.assertNotNull(applicationContext.getBeanDefinitionCount());
    }
}
