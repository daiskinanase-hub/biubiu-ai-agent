//package com.yupi.yuaiagent.tools;
//
//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.Disabled;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.test.context.SpringBootTest;
//
//@SpringBootTest
//class WebSearchToolTest {
//
//    @Value("${bocha.api-key:}")
//    private String bochaApiKey;
//
//    @Test
//    @Disabled("需要配置博查AI API Key才能运行此测试")
//    void searchWeb() {
//        // 确保配置了 API Key
//        Assertions.assertNotNull(bochaApiKey, "请在 application.yml 中配置 bocha.api-key");
//        Assertions.assertFalse(bochaApiKey.isEmpty(), "博查AI API Key 不能为空");
//
//        WebSearchTool webSearchTool = new WebSearchTool(bochaApiKey);
//        String query = "程序员鱼皮编程导航 codefather.cn";
//        String result = webSearchTool.searchWeb(query, "noLimit", 5, true);
//
//        System.out.println("搜索结果: " + result);
//        Assertions.assertNotNull(result);
//        Assertions.assertFalse(result.startsWith("Error"), "搜索出错: " + result);
//    }
//
//    @Test
//    @Disabled("需要配置博查AI API Key才能运行此测试")
//    void searchWebWithFreshness() {
//        Assertions.assertNotNull(bochaApiKey);
//        Assertions.assertFalse(bochaApiKey.isEmpty());
//
//        WebSearchTool webSearchTool = new WebSearchTool(bochaApiKey);
//        String query = "人工智能最新进展";
//        // 搜索一周内的内容
//        String result = webSearchTool.searchWeb(query, "oneWeek", 5, true);
//
//        System.out.println("搜索结果(一周内): " + result);
//        Assertions.assertNotNull(result);
//        Assertions.assertFalse(result.startsWith("Error"), "搜索出错: " + result);
//    }
//
//    @Test
//    @Disabled("需要配置博查AI API Key才能运行此测试")
//    void searchWebSimple() {
//        Assertions.assertNotNull(bochaApiKey);
//        Assertions.assertFalse(bochaApiKey.isEmpty());
//
//        WebSearchTool webSearchTool = new WebSearchTool(bochaApiKey);
//        String query = "Spring Boot 教程";
//        String result = webSearchTool.searchWebSimple(query);
//
//        System.out.println("搜索结果(简化版): " + result);
//        Assertions.assertNotNull(result);
//        Assertions.assertFalse(result.startsWith("Error"), "搜索出错: " + result);
//    }
//}
