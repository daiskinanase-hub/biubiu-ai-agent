# RAG 查询链路需求文档（最终确认版）

> 项目：`rag-etl-pipeline`  
> 状态：**已确认，待进入开发**  
> 核心约束：**禁止修改任何现有 Java 源码**，仅通过新增类、接口、配置完成实现；仅在用户明确授权的 10.1 方案 B 中，对 ETL 链路做必要最小侵入的参数透传。

---

## 1. 项目现状

- **技术栈**：Spring Boot 3.5 + Java 21 + Maven + Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.0
- **向量库**：PGVector（`spring-ai-starter-vector-store-pgvector`）
- **已有链路**：ETL（文档解析 → 语义切块 → 元数据增强 → 向量入库）
  - 入库元数据字段：`document_id`, `chunk_index`, `label`, `keyword`, `page_number`, `preset_question`
  - 默认 ChatClient 模型：`qwen-turbo`
  - Embedding 模型：`text-embedding-v1`
- **缺失链路**：尚无面向终端用户的 **RAG 问答/检索接口**。

---

## 2. 需求目标

新增一条完整的 **RAG 查询管道**，支持：

1. **标签提取**：对用户提示词调用小 LLM（`qwen-turbo`）提取候选标签列表（Label List）。
2. **混合精确检索**：在 PGVector 中做向量相似度检索，同时满足：
   - **知识库 ID 精确筛选**：前端传入 `knowledgeBaseId`，写入 metadata 后通过 `knowledge_base_id == 'xxx'` 精确匹配；
   - **标签精确限定**：`label IN [标签1, 标签2, ...]`，多标签只要命中一个即保留。
3. **重排精筛**：取检索结果前 10 条，调用 `qwen3-rerank` 重排并选出前 5 条；** relevance_score < 0.3 的文档视为不相关**，若全部低于阈值则按**空数据**处理。
4. **流式生成**：将筛选后的文档拼接为上下文，调用大 LLM（`qwen3-max`）进行流式回答；若检索结果为空，则**走正常查询逻辑**（直接由 `qwen3-max` 流式回答，不带上下文）。
5. **输出协议**：使用 Spring MVC `SseEmitter` 向前端推送 SSE 流。

---

## 3. 关键技术验证结论

### 3.1 PGVector Filter 不支持模糊匹配（已源码级验证）

通过对 `spring-ai-vector-store-1.1.2.jar` 内置 ANTLR Grammar（`Filters.g4`）的检查，`compare` 规则仅支持 `EQUALS | GT | GE | LT | LE | NE`，**不存在 `LIKE`/`CONTAINS`**。因此文本模糊匹配无法下推到 SQL 层，解决方式为采用 **精确匹配（`==` 或 `IN`）**。

### 3.2 方案 B 的必要最小侵入说明

用户已选择 **10.1 方案 B**（在 metadata 中写入 `knowledge_base_id`）。由于现有 ETL 接口没有 `knowledgeBaseId` 入参，要实现入库写入，必须对 ETL 链路做以下**最小参数透传改动**（逻辑零变更）：

1. **`EtlPipelineController.process`**：新增可选参数 `@RequestParam(value = "knowledgeBaseId", required = false) String knowledgeBaseId`；若未传则默认使用 `documentId`。
2. **`EtlPipelineService.runPipeline`**：新增 `String knowledgeBaseId` 参数，并透传给 `enrichAndBuildDocuments`。
3. **`EtlPipelineService.enrichAndBuildDocuments`**：新增 `String knowledgeBaseId` 参数，透传给 `toMetadataMap`。
4. **`MetadataEnhancementService.toMetadataMap`**：在返回的 Map 中增加 `map.put("knowledge_base_id", knowledgeBaseId);`。

> 以上 4 处改动是实现方案 B 的**必要条件**。若后续确认不接受，则需回退到方案 A（映射表）。

---

## 4. 整体流程设计

```
前端请求 (knowledgeBaseId + prompt)
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. 标签提取（LabelExtractionService）                       │
│    模型：qwen-turbo（复用现有默认 ChatClient）               │
│    输出：List<String> labels                                │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. 向量检索 + 精确过滤（RagQueryService）                   │
│    a) 构造 SearchRequest                                    │
│       - query = 用户原始 prompt                             │
│       - topK = 10                                           │
│       - filterExpression =                                  │
│         knowledge_base_id == 'kbId'  AND  label IN [labels] │
│         （若 labels 为空，则仅保留 knowledge_base_id 条件） │
│    b) 调用 vectorStore.similaritySearch(...)                │
│    c) 若返回为空，标记为“空数据”                             │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. 重排（RerankService）                                    │
│    模型：qwen3-rerank                                       │
│    API：POST https://dashscope.aliyuncs.com/compatible-api/v1/reranks
│    输入：用户原始 prompt + 检索到的最多 10 条文档文本        │
│    输出：重排后的结果（含 relevance_score）                 │
│    阈值处理：                                                │
│       - 仅保留 relevance_score >= 0.3 的结果                 │
│       - 取保留后的前 5 条                                    │
│       - 若保留后为空，则视为“空数据”                         │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. LLM 流式生成（GenerationService）                        │
│    模型：qwen3-max（新建 ChatClient）                        │
│    分支 A（有有效上下文）：拼接入 System Prompt，通过         │
│         SseEmitter 分段推送回答。                            │
│    分支 B（空数据）：直接发送用户 prompt，通过 SseEmitter     │
│         分段推送回答，并附带头部提示。                       │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. 接口设计

### 5.1 基本信息

| 项 | 值 |
|---|---|
| URL | `POST /api/v1/rag/query` |
| Content-Type | `application/json` |
| 响应类型 | `SseEmitter`（`produces = MediaType.TEXT_EVENT_STREAM_VALUE`） |

### 5.2 请求体（Request）

```json
{
  "knowledgeBaseId": "kb-001",
  "prompt": "请介绍文本排序模型的应用场景"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `knowledgeBaseId` | String | 是 | 知识库唯一标识，用于 `knowledge_base_id` 精确筛选。 |
| `prompt` | String | 是 | 用户原始提示词。 |

### 5.3 SSE 推送格式（SseEmitter）

Controller 通过 `SseEmitter.send(SseEmitter.event().data(...))` 推送，每条 `data` 为 JSON 字符串：

```
event: message
data: {"type":"chunk","content":"文本排序模型"}

event: message
data: {"type":"chunk","content":"广泛应用于搜索引擎"}

event: message
data: {"type":"done"}
```

- `type=chunk`：正常内容片段。
- `type=error`：异常提示（推送后调用 `emitter.completeWithError(...)` 或 `emitter.complete()`）。
- `type=done`：流正常结束标记。

---

## 6. 模型与配置

### 6.1 模型分工

| 角色 | 模型 | 调用方式 |
|------|------|----------|
| 标签提取 | `qwen-turbo` | 复用现有 `ChatClient` Bean |
| 重排 | `qwen3-rerank` | `RestClient` 直接调用 DashScope 兼容 API |
| 生成 | `qwen3-max` | 新建 `ChatClient` Bean（指定 model） |

### 6.2 新增配置项（`application.yml`）

```yaml
rag:
  query:
    generation-model: qwen3-max
    rerank-model: qwen3-rerank
    rerank-top-n: 5
    rerank-min-score: 0.3
    rerank-api-url: https://dashscope.aliyuncs.com/compatible-api/v1/reranks
    vector-search-top-k: 10
```

---

## 7. 数据筛选与重排规则

### 7.1 知识库 ID 精确筛选

通过 `FilterExpressionBuilder.eq("knowledge_base_id", knowledgeBaseId)` 实现。

### 7.2 标签精确限定（`label IN`）

- **提取 Prompt**：
  ```text
  你是企业文档分类助手。请从用户问题中提取 1-5 个最相关的文档主题标签，用于精确匹配知识库中的文档分类。
  要求：
  1. 只返回逗号分隔的标签列表；
  2. 不要添加编号、解释或任何额外文字；
  3. 标签应尽量简洁（2-8个字）。

  用户问题：
  {prompt}
  ```
- **解析**：按逗号拆分，去空去重。
- **Filter 构造**：
  ```java
  new FilterExpressionBuilder()
      .and(
          new FilterExpressionBuilder().eq("knowledge_base_id", knowledgeBaseId),
          new FilterExpressionBuilder().in("label", labels.toArray())
      )
      .build()
  ```
- **降级**：若 labels 为空，则仅保留 `knowledge_base_id` 条件。

### 7.3 重排与阈值

1. 输入：最多 10 条检索文本。
2. `top_n = 5`。
3. 解析返回的 `results`（兼容顶层 `results` 和 `output.results` 两种结构）。
4. 过滤 `relevance_score >= 0.3`。
5. 取过滤后的前 5 条；若为空，视为**空数据**。

---

## 8. 异常与降级策略

| 场景 | 处理策略 |
|------|----------|
| 标签提取失败 | 降级为空标签列表，跳过 `label IN`，继续检索。 |
| 向量检索返回空 | 标记为空数据，走 qwen3-max 纯生成。 |
| 重排 API 失败 | 降级为不重排，取向量检索结果前 5 条。 |
| 重排后全部低于 0.3 | 视为空数据，走 qwen3-max 纯生成。 |
| 生成模型调用失败 | 推送 SSE `type=error` 后结束 emitter。 |

---

## 9. 新增/授权改动文件清单

### 9.1 现有文件的授权最小改动（方案 B 必需）

| 文件 | 改动内容 |
|------|----------|
| `EtlPipelineController.java` | `process` 方法新增可选参数 `knowledgeBaseId`，默认等于 `documentId`。 |
| `EtlPipelineService.java` | `runPipeline` 和 `enrichAndBuildDocuments` 新增 `knowledgeBaseId` 参数并透传。 |
| `MetadataEnhancementService.java` | `toMetadataMap` 增加 `map.put("knowledge_base_id", knowledgeBaseId);`。 |

### 9.2 纯新增文件

| 文件 | 说明 |
|------|------|
| `RagQueryController.java` | `/api/v1/rag/query` 接口，返回 `SseEmitter`。 |
| `RagQueryRequest.java` | 请求体 DTO。 |
| `SseEvent.java` | SSE 事件包装 DTO。 |
| `RagQueryConfig.java` | qwen3-max ChatClient Bean、RestClient Bean。 |
| `RagQueryService.java` | 主编排服务。 |
| `LabelExtractionService.java` | 调用 qwen-turbo 提取标签。 |
| `RerankService.java` | 调用 qwen3-rerank。 |
| `GenerationService.java` | 调用 qwen3-max 并通过 SseEmitter 推送流。 |

---

## 10. 用户确认记录

- [x] **10.1 知识库 ID 筛选**：选择 **方案 B**（最小侵入，在 metadata 中写入 `knowledge_base_id`）。
- [x] **10.2 标签匹配**：选择 **方案 A**（提取通用主题标签，`label IN` 精确匹配）。
- [x] **10.3 流式输出格式**：使用 **`SseEmitter`** 推送 SSE 流。

---

**本需求文档已最终确认。接下来将进入代码实现阶段。**
