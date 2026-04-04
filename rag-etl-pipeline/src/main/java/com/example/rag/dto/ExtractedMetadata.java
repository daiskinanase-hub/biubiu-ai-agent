package com.example.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * LLM 结构化输出：切片级元数据。
 *
 * @param label           主题/类别标签（简短中文或英文短语）
 * @param keyword         关键词，建议逗号分隔
 * @param pageNumber      若文本中可推断页码则填写，否则可为 null
 * @param presetQuestion  基于该切片生成的用户可能追问
 */
public record ExtractedMetadata(
        @JsonProperty("label") String label,
        @JsonProperty("keyword") String keyword,
        @JsonProperty("page_number") Integer pageNumber,
        @JsonProperty("preset_question") String presetQuestion
) {
}
