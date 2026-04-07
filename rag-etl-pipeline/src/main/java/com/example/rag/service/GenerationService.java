package com.example.rag.service;

import com.example.rag.dto.SseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * 流式生成服务：使用 qwen3-max 模型生成回答，并通过 SSE 协议向前端推送。
 * <p>
 * 核心流程：
 * <ol>
 *   <li>构建包含上下文的 System Prompt</li>
 *   <li>调用 ChatClient 的流式接口获取响应片段</li>
 *   <li>将每个片段封装为 SSE 事件推送给前端</li>
 *   <li>流结束或异常时安全关闭 emitter</li>
 * </ol>
 * </p>
 *
 * @see SseEmitter
 * @see org.springframework.ai.chat.client.ChatClient#stream()
 */
@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private final ChatClient qwen3MaxChatClient;

    /**
     * 构造流式生成服务。
     *
     * @param qwen3MaxChatClient qwen3-max 专用 ChatClient（来自 RagQueryConfig）
     */
    public GenerationService(@Qualifier("qwen3MaxChatClient") ChatClient qwen3MaxChatClient) {
        this.qwen3MaxChatClient = qwen3MaxChatClient;
    }

    /**
     * 流式生成回答并推送 SSE 事件。
     *
     * @param prompt      用户原始提示词
     * @param contextDocs 检索/重排后的上下文文档（为空则走正常查询逻辑）
     * @param emitter     SSE 发射器
     */
    public void stream(String prompt, List<Document> contextDocs, SseEmitter emitter) {
        String systemText = buildSystemPrompt(contextDocs);
        log.info("[Generation] 最终给大模型的 System Prompt | length={}\n{}"
                , systemText.length(), systemText);
        log.info("[Generation] 最终给大模型的 User Prompt | prompt={}", prompt);

        qwen3MaxChatClient.prompt()
                .system(systemText)
                .user(prompt)
                .stream()
                .content()
                .subscribe(
                        chunk -> sendEvent(emitter, "chunk", chunk),
                        error -> {
                            sendEvent(emitter, "error", error.getMessage());
                            safeCompleteWithError(emitter, error);
                        },
                        () -> {
                            sendEvent(emitter, "done", null);
                            safeComplete(emitter);
                        }
                );
    }

    /**
     * 构建 System Prompt：根据是否有上下文文档决定回复策略。
     *
     * @param contextDocs 检索到的相关文档列表
     * @return 格式化的 System Prompt 文本
     */
    private String buildSystemPrompt(List<Document> contextDocs) {
        if (contextDocs == null || contextDocs.isEmpty()) {
            return """
                    你是企业知识库问答助手。未能在知识库中检索到与用户问题相关的资料。
                    请基于你的通用知识直接回答，并在回答开头说明"未检索到相关知识，以下回答基于模型通用知识"。
                    """;
        }
        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < contextDocs.size(); i++) {
            Document doc = contextDocs.get(i);
            ctx.append("【参考资料 ").append(i + 1).append("】\n");
            ctx.append(doc.getText()).append("\n\n");
        }
        return """
                你是企业知识库问答助手。请严格根据下面提供的参考资料回答用户问题。
                规则：
                1. 如果参考资料足以回答问题，请基于资料进行总结。
                2. 如果参考资料不足以回答问题，请明确说明。
                3. 禁止编造不在参考资料中的内容。

                参考资料：
                """ + ctx;
    }

    /**
     * 向 SSE emitter 发送事件。
     *
     * @param emitter SSE 发射器
     * @param type    事件类型：chunk / error / done
     * @param content 内容片段
     */
    private void sendEvent(SseEmitter emitter, String type, String content) {
        try {
            log.info("[Generation] 发送 SSE 事件 | type={} | content={}",
                    type, content == null ? "null" : content.substring(0, Math.min(200, content.length())));
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(new SseEvent(type, content)));
        } catch (IOException | IllegalStateException e) {
            safeCompleteWithError(emitter, e);
        }
    }

    /**
     * 安全完成 SSE 连接。
     *
     * @param emitter SSE 发射器
     */
    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // emitter 已超时或已完成，忽略异常
        }
    }

    /**
     * 安全关闭 emitter（带错误信息）。
     *
     * @param emitter SSE 发射器
     * @param error   错误对象
     */
    private void safeCompleteWithError(SseEmitter emitter, Throwable error) {
        try {
            emitter.completeWithError(error);
        } catch (IllegalStateException ignored) {
            // emitter 已超时或已完成，忽略异常
        }
    }
}
