package com.example.rag.service;

import com.example.rag.config.DashScopeDataCenterProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;

/**
 * 基于 DashScope DataCenter 类目解析（与官方 LlamaIndex DashScopeParse 同源 HTTP 流程）：
 * 申请上传租约 → OSS 直传 → 注册解析任务 → 轮询状态 → 下载 DocMind 结构化结果并去噪为可读 Markdown 文本。
 */
@Service
public class DocumentParsingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParsingService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final DashScopeDataCenterProperties dataCenterProperties;
    private final String dashScopeApiKey;

    /**
     * 构造解析服务。
     *
     * @param objectMapper            JSON 处理
     * @param dataCenterProperties    DataCenter 路径与工作区、类目配置
     * @param dashScopeApiKey         DashScope API Key（Bearer）
     */
    public DocumentParsingService(
            ObjectMapper objectMapper,
            DashScopeDataCenterProperties dataCenterProperties,
            @Value("${spring.ai.dashscope.api-key}") String dashScopeApiKey) {
        this.objectMapper = objectMapper;
        this.dataCenterProperties = dataCenterProperties;
        this.dashScopeApiKey = dashScopeApiKey;
        this.restClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(dataCenterProperties.baseUrl()))
                .build();
    }

    /**
     * 上传原始文件并异步解析，返回清洗后的 Markdown 纯文本。
     *
     * @param file 用户上传的 multipart 文件
     * @return Markdown 或近似结构化纯文本（由 DocMind JSON 扁平化而来）
     * @throws IOException 读取文件或网络失败
     */
    public String parseToMarkdown(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            originalFilename = "upload.bin";
        }
        byte[] bytes = file.getBytes();
        return parseToMarkdown(bytes, originalFilename, file.getContentType());
    }

    /**
     * 以字节形式触发解析（便于测试）。
     *
     * @param fileBytes         原始文件内容
     * @param originalFilename  原始文件名（含扩展名）
     * @return Markdown 纯文本
     */
    public String parseToMarkdown(byte[] fileBytes, String originalFilename) {
        return parseToMarkdown(fileBytes, originalFilename, null);
    }

    public String parseToMarkdown(byte[] fileBytes, String originalFilename, String contentType) {
        if (!StringUtils.hasText(dashScopeApiKey) || "your_dashscope_key".equals(dashScopeApiKey)) {
            throw new IllegalStateException("请在环境变量 DASHSCOPE_API_KEY 或 spring.ai.dashscope.api-key 中配置有效的 API Key。");
        }
        String md5 = md5Hex(fileBytes);
        String categoryId = dataCenterProperties.categoryId();
        // 申请上传租约，获取预签名 URL 与 lease_id
        JsonNode leaseNode = postJson(
                "/api/v1/datacenter/category/%s/upload_lease".formatted(categoryId),
                objectMapper.createObjectNode()
                        .put("file_name", basename(originalFilename))
                        .put("size_bytes", fileBytes.length)
                        .put("content_md5", md5));
        String leaseId = requireText(leaseNode, "lease_id");
        JsonNode param = leaseNode.get("param");
        String uploadMethod = param.get("method").asText();
        String uploadUrl = param.get("url").asText();
        HttpHeaders uploadHeaders = readHeaders(param.get("headers"));
        // 将二进制上传到 OSS 预签名地址
        uploadToLease(uploadUrl, uploadMethod, uploadHeaders, fileBytes, contentType);
        // 注册解析任务，拿到 file_id
        JsonNode addFileData = postJson(
                "/api/v1/datacenter/category/%s/add_file".formatted(categoryId),
                objectMapper.createObjectNode()
                        .put("lease_id", leaseId)
                        .put("parser", dataCenterProperties.parser()));
        String fileId = requireText(addFileData, "file_id");
        log.info("DashScope 解析任务已提交 file_id={}", fileId);
        // 轮询解析结果直至成功或失败
        waitUntilParsed(fileId, categoryId);
        // 申请下载租约并拉取 DocMind JSON
        JsonNode downloadLease = postJson(
                "/api/v1/datacenter/category/%s/file/%s/download_lease".formatted(categoryId, fileId),
                objectMapper.createObjectNode().put("file_id", fileId));
        JsonNode dlParam = downloadLease.get("param");
        String dlMethod = dlParam.get("method").asText();
        String dlUrl = dlParam.get("url").asText();
        HttpHeaders dlHeaders = readHeaders(dlParam.get("headers"));
        String rawJson = fetchDownloadBody(dlUrl, dlMethod, dlHeaders);
        // 将 DocMind 结构转为可入库的 Markdown 风格文本
        return docmindJsonToMarkdown(rawJson);
    }

    /**
     * 轮询 query 接口直到 PARSE_SUCCESS 或超时。
     *
     * @param fileId     远端文件 id
     * @param categoryId 类目
     */
    private void waitUntilParsed(String fileId, String categoryId) {
        long deadline = Instant.now().getEpochSecond() + dataCenterProperties.maxWaitSeconds();
        String queryPath = "/api/v1/datacenter/category/%s/file/%s/query".formatted(categoryId, fileId);
        while (true) {
            JsonNode data = postJson(
                    queryPath,
                    objectMapper.createObjectNode().put("file_id", fileId));
            String status = requireText(data, "status");
            if ("PARSE_SUCCESS".equals(status)) {
                return;
            }
            if ("PARSE_FAILED".equals(status)) {
                throw new IllegalStateException("DashScope 文档解析失败 file_id=" + fileId);
            }
            if (Instant.now().getEpochSecond() > deadline) {
                throw new IllegalStateException("DashScope 文档解析超时 file_id=" + fileId);
            }
            try {
                Thread.sleep(Math.max(1, dataCenterProperties.pollIntervalSeconds()) * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待解析被中断", e);
            }
        }
    }

    /**
     * 解析 POST 返回体为 data 节点并校验 code。
     *
     * @param path 相对路径
     * @param body 请求 JSON
     * @return data 节点
     */
    private JsonNode postJson(String path, JsonNode body) {
        String responseText = restClient.post()
                .uri(path)
                .headers(this::applyDashScopeHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(String.class);
        return unwrapData(responseText, path);
    }

    /**
     * 上传字节至预签名 URL。
     *
     * @param url           预签名地址
     * @param method        HTTP 方法（PUT/POST）
     * @param signedHeaders 需透传的请求头
     * @param fileBytes     文件体
     */
    private void uploadToLease(String url, String method, HttpHeaders signedHeaders,
                               byte[] fileBytes, String contentType) {
        RestClient.RequestBodySpec spec;
        if ("PUT".equalsIgnoreCase(method)) {
            spec = restClient.put().uri(URI.create(url)).headers(h -> h.addAll(signedHeaders));
        } else if ("POST".equalsIgnoreCase(method)) {
            spec = restClient.post().uri(URI.create(url)).headers(h -> h.addAll(signedHeaders));
        } else {
            throw new IllegalStateException("不支持的上传方法: " + method);
        }
        if (StringUtils.hasText(contentType)) {
            spec.contentType(MediaType.parseMediaType(contentType));
        }
        spec.body(fileBytes).retrieve().toBodilessEntity();
    }

    /**
     * 根据下载租约拉取原始 JSON 字符串。
     *
     * @param url     下载地址
     * @param method  HTTP 方法
     * @param headers 头
     * @return 响应体字符串
     */
    private String fetchDownloadBody(String url, String method, HttpHeaders headers) {
        if (!"GET".equalsIgnoreCase(method)) {
            throw new IllegalStateException("仅实现 GET 下载，实际为: " + method);
        }
        return restClient.get()
                .uri(URI.create(url))
                .headers(h -> h.addAll(headers))
                .retrieve()
                .body(String.class);
    }

    /**
     * 统一附加 DashScope 鉴权与工作区头。
     *
     * @param headers 目标请求头
     */
    private void applyDashScopeHeaders(HttpHeaders headers) {
        headers.setBearerAuth(dashScopeApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-DashScope-OpenAPISource", "CloudSDK");
        String ws = dataCenterProperties.workspaceId() != null ? dataCenterProperties.workspaceId() : "";
        headers.set("X-DashScope-WorkSpace", ws);
    }

    /**
     * 从 API 响应解析 data，并校验成功码。
     *
     * @param responseText 原始响应文本
     * @param path         调用路径（便于排错）
     * @return data 节点
     */
    private JsonNode unwrapData(String responseText, String path) {
        try {
            JsonNode root = objectMapper.readTree(responseText);
            String code = root.path("code").asText("");
            if (!"Success".equalsIgnoreCase(code) && !"success".equalsIgnoreCase(code)) {
                throw new IllegalStateException("DashScope 调用失败 path=%s body=%s".formatted(path, responseText));
            }
            JsonNode data = root.get("data");
            if (data == null || data.isNull()) {
                throw new IllegalStateException("DashScope 响应缺少 data path=%s".formatted(path));
            }
            return data;
        } catch (IOException e) {
            throw new IllegalStateException("解析 DashScope 响应 JSON 失败 path=%s".formatted(path), e);
        }
    }

    private static String requireText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual()) {
            throw new IllegalStateException("缺少字段 " + field + " 于节点: " + node);
        }
        return v.asText();
    }

    private static HttpHeaders readHeaders(JsonNode headersNode) {
        HttpHeaders headers = new HttpHeaders();
        if (headersNode == null || !headersNode.isObject()) {
            return headers;
        }
        Iterator<Map.Entry<String, JsonNode>> it = headersNode.properties().iterator();
        // 将服务端返回的签名头原样转发至 OSS / 下载网关
        it.forEachRemaining(e -> headers.add(e.getKey(), e.getValue().asText()));
        return headers;
    }

    private static String basename(String name) {
        int idx = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return idx >= 0 ? name.substring(idx + 1) : name;
    }

    private static String trimTrailingSlash(String base) {
        if (base == null) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static String md5Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }

    /**
     * 将 DocMind 返回的 JSON 文本转为近似 Markdown 的纯文本（去除多余层级噪声，保留段落与标题语义）。
     *
     * @param rawJson 下载接口返回的 JSON 字符串
     * @return 扁平化正文
     */
    private String docmindJsonToMarkdown(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root.hasNonNull("markdown") && root.get("markdown").isTextual()) {
                return root.get("markdown").asText();
            }
            StringBuilder sb = new StringBuilder();
            collectDocmindText(root, sb);
            String out = sb.toString().trim();
            return out.isEmpty() ? rawJson : out;
        } catch (IOException e) {
            log.warn("DocMind 结果非 JSON，按原文返回: {}", e.getMessage());
            return rawJson;
        }
    }

    /**
     * 深度优先遍历，抽取 text/content/title 等可读字段。
     * <p>修复：若当前节点已包含 paragraph/text/markdown 等完整内容字段，
     * 则不再递归遍历子节点，避免父子节点重复包含同一段文本。</p>
     *
     * @param node 当前 JSON 节点
     * @param sb   文本累加器
     */
    private void collectDocmindText(JsonNode node, StringBuilder sb) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode c : node) {
                collectDocmindText(c, sb);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        // 检查当前节点是否已包含完整内容字段
        boolean hasCompleteContent = node.hasNonNull("paragraph")
                || node.hasNonNull("markdown")
                || node.hasNonNull("text");

        Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            String key = e.getKey();
            JsonNode val = e.getValue();
            if (val != null && val.isTextual()) {
                // 抽取常见正文字段，避免把 id 类短串全部拼入
                if ("text".equals(key) || "content".equals(key) || "markdown".equals(key)
                        || "title".equals(key) || "paragraph".equals(key)) {
                    String t = val.asText().trim();
                    if (StringUtils.hasText(t)) {
                        sb.append(t).append("\n\n");
                    }
                }
            } else if (!hasCompleteContent) {
                // 只有当父节点没有完整内容字段时，才递归遍历子节点
                collectDocmindText(val, sb);
            }
        }
    }
}
