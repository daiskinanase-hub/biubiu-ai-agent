package com.example.rag.service;

import com.example.rag.dto.ExtractedMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 使用 Spring AI {@link ChatClient} 的结构化输出能力，从切片文本抽取可入库元数据。
 */
@Service
public class MetadataEnhancementService {

    private static final Logger log = LoggerFactory.getLogger(MetadataEnhancementService.class);

    private static final int MAX_CHUNK_CHARS = 12_000;

    private final ChatClient chatClient;

    /**
     * 注入用于抽取元数据的 ChatClient。
     *
     * @param chatClient Rag 场景使用的客户端（默认 ragChatClient）
     */
    public MetadataEnhancementService(@Qualifier("ragChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 对单个切片调用 LLM，返回结构化 {@link ExtractedMetadata}。
     * <p>当 API 额度耗尽等不可恢复错误时，降级返回空元数据，避免阻塞整个 ETL 流程。</p>
     *
     * @param chunkText 切片正文（过长时截断尾部以控制成本）
     * @return 模型解析后的元数据对象
     */
    public ExtractedMetadata enhance(String chunkText) {
        String payload = truncate(chunkText, MAX_CHUNK_CHARS);
        // 通过 entity() 启用 JSON Schema 绑定到 Record
        ExtractedMetadata meta = chatClient.prompt()
                .system("""
                        你是企业文档元数据抽取助手。根据给定切片，抽取：
                        1) label：用不超过12个字概括主题；
                        2) keyword：3-8个关键词，中文逗号分隔；
                        3) page_number：若文本出现明确页码则填整数，否则 null；
                        4) preset_question：用户可能提出的一个追问（疑问句）。
                        输出严格遵守 JSON 字段：label, keyword, page_number, preset_question.""")
                .user(u -> u.text("""
                        切片内容如下：
                        ---
                        %s
                        ---
                        """.formatted(payload)))
                .call()
                .entity(ExtractedMetadata.class);
        log.info("LLM 抽取元数据: label='{}', keyword='{}', pageNumber={}, preset_question='{}'",
                meta.label(), meta.keyword(), meta.pageNumber(), meta.presetQuestion());
        return meta;
    }

    /**
     * 将结构化结果与业务字段合并为写入向量库的 metadata Map（可序列化为 JSONB）。
     *
     * @param meta        LLM 抽取结果
     * @param documentId  业务文档 ID
     * @param chunkIndex  切片序号（从 0 开始）
     * @return 供 {@link org.springframework.ai.document.Document} 使用的元数据
     */
    public Map<String, Object> toMetadataMap(ExtractedMetadata meta, String documentId, int chunkIndex) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("document_id", documentId);
        map.put("chunk_index", chunkIndex);
        if (meta != null) {
            putIfHasText(map, "label", meta.label());
            // keyword 存为数组，支持 PG 数组交集查询
            if (StringUtils.hasText(meta.keyword())) {
                String[] keywords = meta.keyword().split("[,,]");
                map.put("keywords", Arrays.stream(keywords).map(String::trim).filter(StringUtils::hasText).toArray(String[]::new));
            }
            if (meta.pageNumber() != null) {
                map.put("page_number", meta.pageNumber());
            }
            putIfHasText(map, "preset_question", meta.presetQuestion());
        }
        return map;
    }

    private static void putIfHasText(Map<String, Object> map, String key, String val) {
        if (StringUtils.hasText(val)) {
            map.put(key, val);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
