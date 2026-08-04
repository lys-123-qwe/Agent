package com.example.youkeda_project.service.assist;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Random;

/**
 * 网易云热评服务 - 支持随机热评 + 按歌曲名精准搜索.
 * <p>
 * 直接调用网易云公开 GET 接口,无需加密,无需外部服务.
 */
@Service
public class HotCommentService {

    private static final Logger log = LoggerFactory.getLogger(HotCommentService.class);

    // 随机热评(免费接口,回退用)
    private static final String RANDOM_API = "https://free.wqwlkj.cn/wqwlapi/wyy_rp.php?type=json";

    // 网易云公开接口
    private static final String SEARCH_URL = "https://music.163.com/api/search/get";
    private static final String COMMENT_URL = "https://music.163.com/api/v1/resource/comments/R_SO_4_%s";

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random rng = new Random();

    // ==================== 公开方法 ====================

    /** 随机热评(回退方案). */
    public String getRandomComment() {
        log.info("Fetching random NetEase hot comment");
        try {
            String body = get(RANDOM_API);
            JsonNode root = objectMapper.readTree(body);
            if (root.path("code").asInt(-1) != 1) {
                throw new RuntimeException("热评接口返回异常");
            }
            return formatComment(
                    root.path("comment").asText(""),
                    root.path("user").asText("匿名"),
                    root.path("sing").asText("未知歌曲"),
                    root.path("like").asInt(0),
                    root.path("time").asText(""));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch random comment: {}", e.getMessage());
            throw new RuntimeException("获取热评失败:" + e.getMessage(), e);
        }
    }

    /**
     * 搜索指定歌曲并返回一条热评.
     */
    public String searchComment(String keyword) {
        log.info("Searching hot comment for: {}", keyword);

        // 1) 搜歌曲
        SongInfo song = searchSong(keyword);
        if (song == null) {
            return "❌ 没找到歌曲「" + keyword + "」,请换个关键词试试.";
        }

        // 2) 获取热评
        String body = get(String.format(COMMENT_URL, song.id) + "?limit=15");
        JsonNode resp;
        try {
            resp = objectMapper.readTree(body);
        } catch (Exception e) {
            log.error("Parse comment response failed: {}", e.getMessage());
            return "😔 获取评论失败,请稍后再试.";
        }

        JsonNode hot = resp.path("hotComments");
        if (!hot.isArray() || hot.isEmpty()) {
            return "😔 「" + song.name + " - " + song.artist + "」暂无热评,换首歌试试～";
        }

        // 3) 随机挑一条
        int idx = rng.nextInt(hot.size());
        JsonNode c = hot.get(idx);

        String content = c.path("content").asText("");
        String user = c.path("user").path("nickname").asText("匿名");
        int likes = c.path("likedCount").asInt(0);
        long ts = c.path("time").asLong(0);

        return formatComment(content, user, song.name + " - " + song.artist, likes, formatTime(ts));
    }

    // ==================== HTTP 工具 ====================

    private String get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", UA)
                    .header("Referer", "https://music.163.com/")
                    .GET().build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("HTTP GET failed: " + e.getMessage(), e);
        }
    }

    // ==================== 网易云搜索 ====================

    private SongInfo searchSong(String keyword) {
        try {
            String enc = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String body = get(SEARCH_URL + "?s=" + enc + "&type=1&limit=3");

            JsonNode songs = objectMapper.readTree(body).path("result").path("songs");
            if (!songs.isArray() || songs.isEmpty()) {
                log.warn("No songs found for: {}", keyword);
                return null;
            }

            JsonNode first = songs.get(0);
            String id = first.path("id").asText();
            String name = first.path("name").asText("");
            String artist = first.path("artists").get(0).path("name").asText("");

            log.info("Found song: {} - {} (id={})", name, artist, id);
            return new SongInfo(id, name, artist);

        } catch (Exception e) {
            log.error("Song search failed: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 格式化 ====================

    private String formatComment(String content, String user, String song, int likes, String time) {
        StringBuilder sb = new StringBuilder();
        sb.append("💬 \"").append(content).append("\"\n");
        sb.append("    -- ").append(user).append("\n");
        if (song != null && !song.isBlank()) {
            sb.append("    🎵 ").append(song);
        }
        sb.append("  |  ❤️ ").append(likes);
        if (time != null && !time.isBlank()) {
            sb.append("  |  📅 ").append(time);
        }
        return sb.toString();
    }

    private static String formatTime(long ts) {
        if (ts == 0) return "";
        return Instant.ofEpochMilli(ts).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString();
    }

    // ==================== 内部类型 ====================

    private record SongInfo(String id, String name, String artist) {}
}
