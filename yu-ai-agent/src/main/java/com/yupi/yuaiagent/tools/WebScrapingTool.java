package com.yupi.yuaiagent.tools;

import com.yupi.yuaiagent.service.ContentExtractor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 网页抓取工具
 */
public class WebScrapingTool {

    private final ContentExtractor contentExtractor;

    public WebScrapingTool(ContentExtractor contentExtractor) {
        this.contentExtractor = contentExtractor;
    }

    @Tool(description = "Scrape the content of a web page")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            Document document = Jsoup.connect(url)
                    .timeout(30000)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get();
            
            String rawHtml = document.html();
            
            // 使用小模型提取主要内容
            if (contentExtractor != null) {
                String extractedContent = contentExtractor.extractFromWebPage(rawHtml, url);
                return "网页内容摘要：\n" + extractedContent;
            }
            
            // 如果没有内容提取器，返回清理后的文本
            return document.text();
        } catch (Exception e) {
            return "Error scraping web page: " + e.getMessage();
        }
    }
}
