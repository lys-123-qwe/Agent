package com.example.youkeda_project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WechatSearchService {

    private static final Logger log = LoggerFactory.getLogger(WechatSearchService.class);
    private static final String SOGOU_SEARCH_URL = "https://weixin.sogou.com/weixin?type=2&query=";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final HttpClient httpClient;

    public WechatSearchService() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String search(String keyword, int num) {
        if (keyword == null || keyword.isBlank()) {
            return "请输入搜索关键词";
        }

        try {
            String encodedQuery = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = SOGOU_SEARCH_URL + encodedQuery;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String html = response.body();

            log.info("搜狗微信搜索状态码: {}, 响应长度: {}", response.statusCode(), html.length());

            return parseResults(html, num);

        } catch (Exception e) {
            log.error("微信搜索失败", e);
            return "搜索失败: " + e.getMessage();
        }
    }

    private String parseResults(String html, int maxResults) {
        if (html.contains("antispider") || html.contains("403") || html.contains("captcha")) {
            log.warn("搜狗返回了反爬页面, 响应前500字符: {}", html.substring(0, Math.min(500, html.length())));
            return "微信搜索暂时不可用，请稍后再试";
        }

        List<String[]> items = new ArrayList<>();

        Pattern liPattern = Pattern.compile("<li[^>]*id=\"sogou_vr_11002601_box_\\d+\"[^>]*>(.*?)</li>", Pattern.DOTALL);
        Matcher liMatcher = liPattern.matcher(html);

        while (liMatcher.find() && items.size() < maxResults) {
            String liHtml = liMatcher.group(1);

            String title = extractTagContent(liHtml, "h3", "a");
            if (title == null) continue;
            title = title.replaceAll("<[^>]+>", "").trim();
            if (title.isEmpty()) continue;

            if (items.size() >= maxResults) break;
            String summary = extractContent(liHtml, "txt-info");
            if (summary != null) {
                summary = summary.replaceAll("<[^>]+>", "").trim();
            }

            String sogouUrl = extractHref(liHtml, "h3", "a");

            items.add(new String[]{title, summary != null ? summary : "", sogouUrl != null ? sogouUrl : ""});
        }

        if (items.isEmpty()) {
            log.warn("搜狗返回的页面未解析到文章, 状态码200但无匹配, 页面片段: {}",
                html.substring(0, Math.min(300, html.length())));
            return "没有找到相关的微信公众号文章";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("微信公众号搜索结果：\n");
        int num = 1;
        for (String[] item : items) {
            sb.append(num).append(". **").append(item[0]).append("**");
            if (!item[1].isBlank()) {
                String snippet = item[1].replaceAll("\\s+", " ").trim();
                if (snippet.length() > 60) {
                    snippet = snippet.substring(0, 60) + "…";
                }
                sb.append(" — ").append(snippet);
            }
            if (!item[2].isBlank()) {
                sb.append(" [详情](").append("https://weixin.sogou.com").append(item[2]).append(")");
            }
            sb.append("\n");
            num++;
        }
        return sb.toString();
    }

    private String extractTagContent(String html, String parentTag, String childTag) {
        Pattern p = Pattern.compile("<" + parentTag + "[^>]*>.*?<" + childTag + "[^>]*>(.*?)</" + childTag + ">.*?</" + parentTag + ">", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        return m.find() ? m.group(1) : null;
    }

    private String extractContent(String html, String className) {
        Pattern p = Pattern.compile("<p[^>]*class=\"[^\"]*" + className + "[^\"]*\"[^>]*>(.*?)</p>", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        return m.find() ? m.group(1) : null;
    }

    private String extractHref(String html, String parentTag, String childTag) {
        Pattern p = Pattern.compile("<" + parentTag + "[^>]*>.*?<" + childTag + "[^>]*href=\"([^\"]+)\".*?</" + parentTag + ">", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        return m.find() ? m.group(1) : null;
    }
}