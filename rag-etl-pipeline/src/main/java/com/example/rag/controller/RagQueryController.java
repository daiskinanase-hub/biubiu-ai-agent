package com.example.rag.controller;

import com.example.rag.dto.RagQueryRequest;
import com.example.rag.dto.RagQueryResponse;
import com.example.rag.service.RagQueryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 查询接口：接收用户问题并返回非流式同步回答。
 */
@RestController
@RequestMapping("/api/v1/rag")
public class RagQueryController {

    private final RagQueryService ragQueryService;

    public RagQueryController(RagQueryService ragQueryService) {
        this.ragQueryService = ragQueryService;
    }

    /**
     * 触发 RAG 查询，返回完整回答（非流式）。
     *
     * @param request 包含 documentId 与 prompt
     * @return 包含 answer 和 docCount 的响应
     */
    @PostMapping("/query")
    public RagQueryResponse query(@RequestBody RagQueryRequest request) {
        return ragQueryService.query(request);
    }
}