//package com.yupi.yuaiagent.agent;
//
//import jakarta.annotation.Resource;
//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.api.Test;
//import org.springframework.boot.test.context.SpringBootTest;
//
//@SpringBootTest
//class YuManusTest {
//
//    @Resource
//    private YuManus yuManus;
//
//    @Test
//    public void run() {
//        String userPrompt = "查询合肥今天的天气";
//        String answer = yuManus.run(userPrompt);
//        Assertions.assertNotNull(answer);
//    }
//}