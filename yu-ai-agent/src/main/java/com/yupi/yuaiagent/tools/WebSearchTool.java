package com.yupi.yuaiagent.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yupi.yuaiagent.service.ContentExtractor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具 - 使用博查AI Web Search API
 */
public class WebSearchTool {

    // 博查AI Web Search API 端点
    private static final String BOCHA_API_URL = "https://api.bocha.cn/v1/web-search";

    private final String apiKey;
    private final ContentExtractor contentExtractor;

    public WebSearchTool(String apiKey, ContentExtractor contentExtractor) {
        this.apiKey = apiKey;
        this.contentExtractor = contentExtractor;
    }

    /**
     * 搜索网页信息
     *
     * @param query     搜索关键词（必填）
     * @param freshness 时间范围（可选）: noLimit(不限), oneDay(一天内), oneWeek(一周内), 
     *                  oneMonth(一个月内), oneYear(一年内), 或日期范围如 "2025-01-01..2025-04-06"
     * @param count     返回结果数量（可选）: 1-50，默认10
     * @param summary   是否返回文本摘要（可选）: true/false，默认false
     * @return 搜索结果JSON字符串
     */
    @Tool(description = "Search for information from Web using Bocha AI Search API")
    public String searchWeb(
            @ToolParam(description = "Search query keyword", required = true) String query,
            @ToolParam(description = "Time range filter: noLimit, oneDay, oneWeek, oneMonth, oneYear, or date range like '2025-01-01..2025-04-06' (optional, default: noLimit)") String freshness,
            @ToolParam(description = "Number of results to return, range 1-50 (optional, default: 10)") Integer count,
            @ToolParam(description = "Whether to include text summary for each result (optional, default: false)") Boolean summary) {
        
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", query);
        
        // 设置默认值
        if (freshness != null && !freshness.isEmpty()) {
            requestBody.put("freshness", freshness);
        } else {
            requestBody.put("freshness", "noLimit");
        }
        
        if (count != null && count > 0 && count <= 50) {
            requestBody.put("count", count);
        } else {
            requestBody.put("count", 10);
        }
        
        if (summary != null) {
            requestBody.put("summary", summary);
        }

        try {
            // 发送 POST 请求
            HttpResponse response = HttpRequest.post(BOCHA_API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(30000)
                    .execute();

            if (response.getStatus() != 200) {
                return "Error searching web: HTTP " + response.getStatus() + " - " + response.body();
            }

            // 解析响应
            JSONObject jsonResponse = JSONUtil.parseObj(response.body());
            
            // 检查返回码
            Integer code = jsonResponse.getInt("code");
            if (code == null || code != 200) {
                return "Error searching web: API returned code " + code;
            }

            // 提取 webPages 数据
            JSONObject data = jsonResponse.getJSONObject("data");
            if (data == null) {
                return "No search results found";
            }

            JSONObject webPages = data.getJSONObject("webPages");
            if (webPages == null) {
                return "No web pages found in search results";
            }

            JSONArray valueArray = webPages.getJSONArray("value");
            if (valueArray == null || valueArray.isEmpty()) {
                return "No web page results available";
            }

            // 取前5条结果（如果可用）
            int resultCount = Math.min(valueArray.size(), 5);
            List<Object> results = valueArray.subList(0, resultCount);

            // 拼接搜索结果为字符串
            String rawResult = results.stream()
                    .map(obj -> ((JSONObject) obj).toString())
                    .collect(Collectors.joining(","));

            // 使用小模型提取关键信息
            if (contentExtractor != null) {
                String extractedContent = contentExtractor.extractFromSearchResult(rawResult, query);
                return "搜索结果摘要：\n" + extractedContent;
            }

            return rawResult;

        } catch (Exception e) {
            return "Error searching web: " + e.getMessage();
        }
    }

    /**
     * 简化版搜索方法（只传关键词）
     *
     * @param query 搜索关键词
     * @return 搜索结果JSON字符串
     */
    @Tool(description = "Search for information from Web using Bocha AI Search API (simple version with query only)")
    public String searchWebSimple(
            @ToolParam(description = "Search query keyword", required = true) String query) {
        return searchWeb(query, "noLimit", 10, false);
    }
}
