package com.example.rag.interceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置 - 注册拦截器。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequestTracingInterceptor requestTracingInterceptor;

    public WebMvcConfig(RequestTracingInterceptor requestTracingInterceptor) {
        this.requestTracingInterceptor = requestTracingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestTracingInterceptor)
                .addPathPatterns("/api/**")           // 只拦截API请求
                .excludePathPatterns(               // 排除的路径
                        "/api/health",
                        "/api/actuator/**"
                );
    }
}
