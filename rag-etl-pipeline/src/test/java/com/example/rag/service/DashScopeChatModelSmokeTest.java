package com.example.rag.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证 DashScope qwen-turbo 模型是否可正常调用。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Disabled
class DashScopeChatModelSmokeTest {

    @Autowired
    @Qualifier("ragChatClient")
    private ChatClient chatClient;

    @Test
    void qwenTurbo_shouldRespond() {
        String response = chatClient.prompt()
                .user("你好，请只回复一个单词 'pong'")
                .call()
                .content();

        Assertions.assertNotNull(response, "模型应返回非空内容");
        Assertions.assertTrue(response.toLowerCase().contains("pong"),
                "模型返回内容应包含 'pong'，实际返回: " + response);
    }
}
