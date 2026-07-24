package com.example.youkeda_project.service;

import com.example.youkeda_project.config.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private static final String TIAN_API_BASE = "http://api.tianapi.com/generalnews/index";
    private static final String BOCHA_API_URL = "https://api.bocha.cn/v1/web-search";
    private static final Pattern TITLE_TAG = Pattern.compile(
            "<title[^>]*>(.*?)</title>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Set<String> EXCLUDE_KW = Set.of(
            "每日热点", "汇总", "合集", "条新闻", "条热点", "条精选",
            "新闻联播", "新闻速递", "每天精选",
            "简报", "要闻简报", "任课通知", "开标", "招标");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ApiConfig apiConfig;

    public NewsService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public String searchNews(int num, String keyword) {
        return searchNews(num, keyword, null);
    }

    public String searchOfficialNews() {
        String tianKey = apiConfig.getNewsSdkApiKey();
        if (tianKey != null && !tianKey.isBlank()) {
            String result = tianApiTopHeadlines();
            if (!result.startsWith("错误:") && !result.startsWith("没有找到")) {
                return result;
            }
            log.warn("天聚数行返回空，降级到博查: {}", result);
        }

        String apiKey = apiConfig.getNewsApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "错误: 未配置博查搜索 API Key";
        }
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("query", "今日热点 最新消息");
            params.put("freshness", "oneDay");
            params.put("count", 50);
            params.put("category", "News");
            String bodyJson = objectMapper.writeValueAsString(params);
            log.info("博查搜索请求: {}", bodyJson);
            var hreq = HttpRequest.newBuilder()
                    .uri(URI.create(BOCHA_API_URL))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> hresp = httpClient.send(hreq, HttpResponse.BodyHandlers.ofByteArray());
            byte[] responseBytes = hresp.body();
            String responseJson = new String(responseBytes, StandardCharsets.UTF_8);
            log.info("博查搜索响应: {}", responseJson);
            JsonNode root = objectMapper.readTree(responseJson);
            int code = root.path("code").asInt();
            if (code != 200) {
                return "错误: " + root.path("msg").asText("未知错误");
            }
            JsonNode value = root.path("data").path("webPages").path("value");
            if (!value.isArray() || value.isEmpty()) {
                return "没有找到相关结果";
            }

            List<String[]> items = new ArrayList<>();
            Set<String> seenUrls = new LinkedHashSet<>();

            for (JsonNode item : value) {
                String rawTitle = item.path("name").asText("").trim();
                String url = cleanUrl(item.path("url").asText(""));
                String snippet = item.path("snippet").asText("");
                String summary = item.path("summary").asText("");

                if (!isValidArticle(rawTitle, url, snippet, summary)) continue;
                if (url == null || !seenUrls.add(url)) continue;

                String title = cleanTitle(rawTitle);
                if (title.length() < 4) continue;

                if (title.matches("^[\\u4e00-\\u9fa5]{2,15}$")) {
                    String pageTitle = fetchPageTitle(url);
                    if (pageTitle != null && pageTitle.length() > title.length()) {
                        title = cleanTitle(pageTitle);
                    }
                }

                int cnCount = title.replaceAll("[^\\u4e00-\\u9fa5]", "").length();
                if (cnCount < 3) continue;

                items.add(new String[]{title, url});
                if (items.size() >= 20) break;
            }

            if (items.isEmpty()) {
                return "没有找到相关的新闻";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("今日新闻").append("：\n");
            for (int i = 0; i < items.size(); i++) {
                sb.append(i + 1).append(". ").append(items.get(i)[0])
                        .append(" [详情](").append(items.get(i)[1]).append(")\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("今日新闻搜索失败", e);
            return "搜索失败: " + e.getMessage();
        }
    }

    // ========== 天聚数行 ==========

    private String tianApiTopHeadlines() {
        return tianApiSearch(null, 20);
    }

    /**
     * 从天聚数行获取新闻，按关键词客户端过滤。
     * <p>
     * 不传 word 参数（服务器端过滤不准确），而是取最新 50 条头条，
     * 在客户端按标题/来源匹配关键词。关键词太泛（如"今日""新闻"）则不过滤。
     */
    private String tianApiSearch(String keyword, int pageSize) {
        try {
            String apiKey = apiConfig.getNewsSdkApiKey();
            int num = Math.min(pageSize, 50);
            String url = TIAN_API_BASE + "?key=" + apiKey + "&num=" + Math.max(num, 50);
            log.info("天聚数行请求: {}", url.replace(apiKey, "***"));
            var hreq = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
            HttpResponse<byte[]> hresp = httpClient.send(hreq, HttpResponse.BodyHandlers.ofByteArray());
            byte[] responseBytes = hresp.body();
            return tianApiParseResponse(responseBytes, keyword, "今日新闻");
        } catch (Exception e) {
            log.error("天聚数行失败", e);
            return "新闻获取失败: " + e.getMessage();
        }
    }

    /**
     * 解析天聚数行响应，可选客户端关键词过滤。
     */
    private String tianApiParseResponse(byte[] responseBytes, String keyword, String fallbackPrefix) {
        try {
            String json = new String(responseBytes, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(json);
            int code = root.path("code").asInt(0);
            if (code != 200) {
                return "错误: " + root.path("msg").asText("未知错误");
            }
            JsonNode newslist = root.path("newslist");
            if (!newslist.isArray() || newslist.isEmpty()) {
                return "没有找到相关新闻";
            }

            boolean doFilter = keyword != null && !keyword.isBlank()
                && !isGenericQuery(keyword);
            String prefix = doFilter ? keyword : fallbackPrefix;

            List<String[]> allItems = new ArrayList<>();
            Set<String> seenUrls = new LinkedHashSet<>();
            Map<String, Integer> sourceCount = new java.util.HashMap<>();
            for (JsonNode article : newslist) {
                String title = article.path("title").asText("").trim();
                String rawUrl = article.path("url").asText("").trim();
                String url = cleanUrl(rawUrl);
                String source = article.path("source").asText("");
                if (title.isEmpty() || url == null || url.isEmpty()) continue;
                String clean = cleanTitle(title);
                if (clean.length() < 4) continue;
                if (!seenUrls.add(url)) continue;
                if (doFilter && !matchKeyword(title, source, keyword)) continue;
                // 同一来源最多取 3 条，避免结果单一
                int srcMax = 3;
                if (!source.isEmpty() && sourceCount.merge(source, 1, Integer::sum) > srcMax) continue;
                String display = source.isEmpty() ? clean : clean + " [" + source + "]";
                allItems.add(new String[]{display, url});
                if (allItems.size() >= 20) break;
            }
            if (allItems.isEmpty()) {
                return doFilter ? "没有找到与关键词匹配的结果" : "没有找到相关新闻";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(prefix).append("：\n");
            for (int i = 0; i < allItems.size(); i++) {
                sb.append(i + 1).append(". ").append(allItems.get(i)[0])
                    .append(" [详情](").append(allItems.get(i)[1]).append(")\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("解析天聚数行响应失败", e);
            return "解析新闻结果失败: " + e.getMessage();
        }
    }

    /**
     * 判断关键词是否太泛（自然语言/无具体指向），是则不过滤直接返回全部头条。
     */
    private boolean isGenericQuery(String keyword) {
        String kw = keyword.replaceAll("[\\s,，、。！？;；:：\"\"''【】（）\\[\\]{}]", "").toLowerCase();
        if (kw.isEmpty()) return true;
        // 纯中文短词匹配预定义集合
        Set<String> generic = Set.of(
                "今日", "新闻", "最新", "热点", "推荐", "搜索", "今天",
                "今日新闻", "最新新闻", "今天新闻", "今日热点", "今天热点",
                "最新消息", "今日最新", "新闻热点", "热门新闻",
                "刚刚", "要闻", "头条", "快讯",
                "今日最新新闻", "今天有什么新闻", "最近新闻", "最近热点");
        if (kw.matches("^[\\u4e00-\\u9fa5]{1,10}$") && generic.contains(kw)) {
            return true;
        }
        return false;
    }

    /**
     * 客户端模糊匹配：关键词分词后任一有意义的 token 出现在标题或来源中即算匹配。
     */
    private boolean matchKeyword(String title, String source, String keyword) {
        String kw = keyword.trim().toLowerCase();
        String t = (title + " " + source).toLowerCase();
        if (t.contains(kw)) return true;
        String[] tokens = kw.split("[\\s,，、；;]+");
        if (tokens.length > 1) {
            for (String token : tokens) {
                token = token.trim();
                if (token.length() >= 3 && !token.matches("\\d+") && t.contains(token)) return true;
            }
        }
        return false;
    }

    // ========== 博查 ==========

    private String searchNews(int num, String keyword, String category) {
        // 1) 天聚数行：新闻类查询首选
        String tianKey = apiConfig.getNewsSdkApiKey();
        if (tianKey != null && !tianKey.isBlank()) {
            String result = tianApiSearch(keyword, num);
            // 有匹配结果或关键词太泛（直接返回头条）→ 直接返回
            if (!result.startsWith("没有找到")) {
                return result;
            }
            // 空结果且是具体关键词 → 降级到博查（可能是非新闻类查询）
            if (!isGenericQuery(keyword)) {
                log.warn("天聚数行无匹配，降级到博查搜索: {}", keyword);
                String bocha = bochaSearch(keyword, num);
                if (!bocha.startsWith("没有找到") && !bocha.startsWith("错误:")) {
                    return bocha;
                }
            }
            // 兜底：头条新闻
            return tianApiTopHeadlines();
        }

        // 2) 博查（仅当天聚数行未配置时）
        return bochaSearch(keyword, num);
    }

    /**
     * 博查搜索，过滤宽松（用于非新闻类通用查询）。
     */
    private String bochaSearch(String keyword, int pageSize) {
        return bochaSearch(keyword, pageSize, null);
    }

    private String bochaSearch(String keyword, int pageSize, String category) {
        String apiKey = apiConfig.getNewsApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "错误: 未配置博查搜索 API Key";
        }
        int count = Math.min(pageSize * 2, 50);
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("query", keyword);
            params.put("summary", true);
            params.put("count", count);
            if (category != null && !category.isBlank()) {
                params.put("category", category);
                params.put("freshness", "oneDay");
            }
            String bodyJson = objectMapper.writeValueAsString(params);
            log.info("博查搜索请求: {}", bodyJson);
            var hreq = HttpRequest.newBuilder()
                    .uri(URI.create(BOCHA_API_URL))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> hresp = httpClient.send(hreq, HttpResponse.BodyHandlers.ofByteArray());
            byte[] responseBytes = hresp.body();
            String responseJson = new String(responseBytes, StandardCharsets.UTF_8);
            log.info("博查搜索响应: {}", responseJson);
            return bochaSearchParse(responseJson, keyword, pageSize);
        } catch (Exception e) {
            log.error("博查搜索失败", e);
            return "搜索失败: " + e.getMessage();
        }
    }

    /**
     * 解析博查搜索结果（宽松过滤，保留百科/资讯/官网等）。
     */
    private String bochaSearchParse(String json, String keyword, int maxItems) {
        try {
            JsonNode root = objectMapper.readTree(json);
            int code = root.path("code").asInt();
            if (code != 200) return "错误: " + root.path("msg").asText("未知错误");
            JsonNode value = root.path("data").path("webPages").path("value");
            if (!value.isArray() || value.isEmpty()) return "没有找到相关结果";
            List<String[]> items = new ArrayList<>();
            Set<String> seenUrls = new LinkedHashSet<>();
            Set<String> seenNames = new LinkedHashSet<>();
            for (JsonNode item : value) {
                String rawTitle = item.path("name").asText("").trim();
                String url = cleanUrl(item.path("url").asText(""));
                String snippet = item.path("snippet").asText("");
                if (rawTitle.isEmpty() || url == null) continue;
                String title = cleanTitle(rawTitle);
                if (title.length() < 4) continue;
                if (!seenUrls.add(url)) continue;
                if (!seenNames.add(title)) continue;
                String urlLower = url.toLowerCase();
                if (urlLower.contains("yqtweb") || urlLower.contains("caifuhao")
                    || urlLower.contains("stockstar") || urlLower.contains("/content_"))
                    continue;
                String display = title;
                if (!snippet.isBlank()) {
                    String s = snippet.replaceAll("\\s+", " ").trim();
                    if (s.length() > 80) s = s.substring(0, 80) + "…";
                    display = title + " — " + s;
                }
                items.add(new String[]{display, url});
                if (items.size() >= maxItems) break;
            }
            if (items.isEmpty()) return "没有找到相关结果";
            StringBuilder sb = new StringBuilder();
            sb.append(keyword).append("搜索结果：\n");
            for (int i = 0; i < items.size(); i++) {
                sb.append(i + 1).append(". ").append(items.get(i)[0])
                    .append(" [详情](").append(items.get(i)[1]).append(")\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("解析博查搜索响应失败", e);
            return "解析搜索结果失败: " + e.getMessage();
        }
    }

    // ========== 工具方法 ==========

    private boolean isValidArticle(String title, String url, String snippet, String summary) {
        if (title.isEmpty() || url == null || url.isBlank()) return false;
        String urlLower = url.toLowerCase();
        if (urlLower.contains("toutiao.com") || urlLower.contains("k.sina.com")
                || urlLower.contains(".gov.cn") || urlLower.contains("/column/")
                || urlLower.contains("/layout/") || urlLower.contains("/paper/")
                || urlLower.contains("node_") || urlLower.contains("bbs.hupu.com")
                || urlLower.contains("tgb.cn") || urlLower.contains("tieba.baidu.com")
                || urlLower.contains("caifuhao") || urlLower.contains("/content_")
                || urlLower.contains("yqtweb") || urlLower.contains("stockstar"))
            return false;
        for (String kw : EXCLUDE_KW) {
            if (title.contains(kw)) return false;
        }
        if (isNewsDigest(title)) return false;
        String titleClean = cleanTitle(title);
        if (titleClean.matches("^[\\u4e00-\\u9fa5]{1,4}$")) return false;
        if (isGenericTitle(titleClean)) return false;
        return true;
    }

    private boolean isNewsDigest(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) return false;
        if (rawTitle.split("[；;]").length > 1) return true;
        String[] digestKws = {"早新闻", "早读", "晨报", "晚报", "午报", "速览", "一览",
                "快报", "新闻早餐", "新闻来了", "新闻早班车", "新闻联播",
                "读报", "夜读", "早间", "今日焦点", "新闻速递",
                "小报", "小板报", "社区报", "简报", "要闻简报"};
        String t = rawTitle.toLowerCase();
        for (String kw : digestKws) {
            if (t.contains(kw)) return true;
        }
        int cnt = 0;
        for (int i = 0; i < rawTitle.length(); i++) {
            if (rawTitle.charAt(i) == '！') {
                cnt++;
                if (cnt >= 3) return true;
            }
        }
        return false;
    }

    private String fetchPageTitle(String url) {
        try {
            var hreq = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .GET()
                    .build();
            HttpResponse<byte[]> hresp = httpClient.send(hreq, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = hresp.body();
            if (body == null || body.length == 0) return null;
            Charset charset = detectCharset(body);
            String head = new String(body, charset);
            Matcher m = TITLE_TAG.matcher(head);
            if (m.find()) {
                String t = m.group(1).trim();
                if (!t.isEmpty()) return t;
            }
            String headIso = new String(body, StandardCharsets.ISO_8859_1);
            Matcher m2 = TITLE_TAG.matcher(headIso);
            if (m2.find()) {
                String t = m2.group(1).trim();
                t = t.replaceAll("\\s+", " ").trim();
                if (!t.isEmpty()) return t;
            }
            return null;
        } catch (Exception e) {
            log.debug("获取页面标题失败: {}", url);
            return null;
        }
    }

    private Charset detectCharset(byte[] body) {
        String head = new String(body, 0, Math.min(body.length, 4096), StandardCharsets.ISO_8859_1);
        Matcher m = Pattern.compile("charset\\s*=\\s*[\"']?([a-zA-Z0-9\\-]+)", Pattern.CASE_INSENSITIVE).matcher(head);
        if (m.find()) {
            try { return Charset.forName(m.group(1).trim()); } catch (Exception ignored) {}
        }
        return StandardCharsets.UTF_8;
    }

    private String cleanTitle(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String t = raw.trim();
        t = t.replaceAll("(?:[_＿][^_＿]+)+$", "").trim();
        t = t.replaceAll("\\s*[-—]\\s*\\S+$", "").trim();
        t = t.replaceAll("^[^|\uFF5C]+[|\uFF5C]", "").trim();
        t = t.replaceAll("^(原标题|快讯|热点|今日关注|最新消息|滚动)[：:]?", "").trim();
        t = t.replaceAll("（[^）]*图[^）]*）$", "").trim();
        t = t.replaceAll("\\([^)]*图[^)]*\\)$", "").trim();
        t = t.replaceAll("[\\[【][^\\]】]*(?:视频|音频|组图|图集|海报|直播|专题)[\\]】]$", "").trim();
        t = t.replaceAll("^[\\[【][^\\]】]*[\\]】]", "").trim();
        t = t.replaceAll("\\d+张照片$", "").trim();
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    private String cleanUrl(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        if (trimmed.startsWith("//")) {
            trimmed = "https:" + trimmed;
        } else if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }
        return trimmed;
    }

    private boolean isGenericTitle(String title) {
        String[] generic = {"首页", "上一页", "下一页", "返回", "更多", "全部", "搜索",
                "登录", "注册", "关于我们", "联系我们", "网站地图", "版权声明", "免责声明",
                "隐私政策", "广告服务", "今日头版", "要闻", "综合", "时政", "国际", "财经",
                "科技", "文化", "体育", "教育", "健康", "汽车", "房产", "娱乐", "军事",
                "社会", "法治", "专题", "视频", "图片", "热点", "推荐", "头条", "最新",
                "滚动", "直播", "互动", "投稿", "订阅", "新闻", "评论", "观点", "人物",
                "深度", "万象", "媒体", "报纸", "网站", "要闻资讯", "热点关注", "腾讯新闻",
                "网易新闻", "新浪新闻", "搜狐新闻", "凤凰新闻",
                "每日经济", "每日一股", "每日股市", "股市盘点", "股市总结", "股市分析",
                "港股收盘", "A股收盘", "收盘点评", "盘前必读", "复盘", "早报", "晚报",
                "封面早报", "今日早报", "今日晚报", "中原日报", "广州日报", "人民日报",
                "经济日报", "证券日报", "证券时报", "上海证券报", "中国证券报",
                "涨停", "跌停", "ETF", "基金"};
        for (String g : generic) {
            if (title.contains(g)) return true;
        }
        return false;
    }
}
