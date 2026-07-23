package com.example.youkeda_project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;

@Service
public class MusicService {

    private static final Logger log = LoggerFactory.getLogger(MusicService.class);
    private static final String QQ_SEARCH_API = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    private static final String QQ_LYRIC_API = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg";
    private static final String NETEASE_SEARCH_API = "https://music.163.com/api/search/get/web";
    private static final int MAX_SONGS = 5;

    private final RestClient qqClient;
    private final RestClient neteaseClient;
    private final ObjectMapper objectMapper;

    public MusicService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.qqClient = RestClient.builder()
                .defaultHeader("Referer", "https://y.qq.com")
                .build();
        this.neteaseClient = RestClient.builder()
                .defaultHeader("Referer", "https://music.163.com")
                .build();
    }

    public String search(String keyword) {
        return formatResult(keyword, qqSearch(keyword, MAX_SONGS), false);
    }

    public String recommend(String description) {
        String[] keywords;
        String desc = description.trim().toLowerCase();
        if (desc.matches(".*(歌|曲|音乐|听).*")) {
            keywords = new String[]{description, description + " 热门", description + " 经典"};
        } else {
            keywords = new String[]{description + " 歌曲", description + " 音乐", description + " 热门"};
        }

        LinkedHashSet<String> seenMids = new LinkedHashSet<>();
        ArrayList<JsonNode> allSongs = new ArrayList<>();

        for (String kw : keywords) {
            java.util.List<JsonNode> batch = qqSearch(kw, 10);
            for (JsonNode s : batch) {
                String songmid = s.path("songmid").asText();
                if (!songmid.isEmpty() && seenMids.add(songmid)) {
                    allSongs.add(s);
                    if (allSongs.size() >= 10) break;
                }
            }
            if (allSongs.size() >= 10) break;
        }

        if (allSongs.isEmpty()) {
            return "没有找到相关推荐";
        }
        return formatResult(description, allSongs, true);
    }

    private java.util.List<JsonNode> qqSearch(String keyword, int limit) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = QQ_SEARCH_API + "?w=" + encoded + "&format=json&n=" + limit;
            log.info("QQ音乐搜索: {}", url);

            String json = qqClient.get().uri(url).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(json);
            int code = root.path("code").asInt();
            if (code != 0) {
                log.warn("QQ音乐搜索返回错误 code={}", code);
                return neteaseFallback(keyword, limit);
            }

            JsonNode list = root.path("data").path("song").path("list");
            if (!list.isArray() || list.isEmpty()) {
                return neteaseFallback(keyword, limit);
            }

            java.util.List<JsonNode> songs = new ArrayList<>();
            for (JsonNode s : list) {
                songs.add(s);
            }
            return songs;
        } catch (Exception e) {
            log.warn("QQ音乐搜索失败，降级到网易云", e);
            return neteaseFallback(keyword, limit);
        }
    }

    private java.util.List<JsonNode> neteaseFallback(String keyword, int limit) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = NETEASE_SEARCH_API + "?s=" + encoded + "&type=1&offset=0&total=true&limit=" + limit;
            log.info("网易云搜索降级: {}", url);

            String json = neteaseClient.get().uri(url).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(json);
            if (root.path("code").asInt() != 200) return java.util.List.of();

            JsonNode songs = root.path("result").path("songs");
            if (!songs.isArray()) return java.util.List.of();

            java.util.List<JsonNode> result = new ArrayList<>();
            for (JsonNode s : songs) {
                result.add(s);
            }
            return result;
        } catch (Exception e) {
            log.error("网易云搜索也失败", e);
            return java.util.List.of();
        }
    }

    private String formatResult(String title, java.util.List<JsonNode> songs, boolean isRecommend) {
        if (songs.isEmpty()) {
            return "没有找到相关歌曲";
        }

        StringBuilder sb = new StringBuilder();
        if (isRecommend) {
            sb.append("🎵 为你推荐 ").append(title).append(" 相关歌曲：\n\n");
        } else {
            sb.append("🎵 找到以下歌曲：\n\n");
        }

        for (int i = 0; i < songs.size(); i++) {
            appendSongInfo(sb, songs.get(i), i + 1);
        }
        return sb.toString();
    }

    private void appendSongInfo(StringBuilder sb, JsonNode song, int idx) {
        String songmid = song.path("songmid").asText("");
        long songid = song.path("id").asLong();
        if (songid <= 0) songid = song.path("songid").asLong();

        String name = song.path("name").asText("未知");
        String artist = extractQqArtist(song.path("singer"));
        String album = song.path("album").path("name").asText("");
        int seconds = song.path("interval").asInt(0);
        String duration = seconds > 0 ? String.format("%d:%02d", seconds / 60, seconds % 60) : "";

        sb.append(idx).append(". ").append(name)
                .append(" - ").append(artist).append("\n");
        if (!album.isBlank()) sb.append("   专辑: ").append(album).append("\n");
        if (!duration.isBlank()) sb.append("   时长: ").append(duration).append("\n");

        if (!songmid.isBlank()) {
            sb.append("   ▶ 听歌: https://i.y.qq.com/v8/playsong.html?songmid=").append(songmid).append("&source=wechat&ADTAG=wechat\n");
        } else if (songid > 0) {
            sb.append("   ▶ 听歌: https://music.163.com/song?id=").append(songid).append("\n");
        }

        String lyricSnippet = songmid.isBlank() ? null : fetchQqLyric(songmid);
        if (lyricSnippet != null) {
            sb.append("   📝 ").append(lyricSnippet).append("\n");
        }
        sb.append("\n");
    }

    private String fetchQqLyric(String songmid) {
        try {
            String url = QQ_LYRIC_API + "?songmid=" + songmid + "&format=json";
            String json = qqClient.get().uri(url).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(json);
            if (root.path("code").asInt() != 0) return null;

            String lyric = root.path("lyric").asText();
            if (lyric.isBlank()) return null;

            String[] lines = lyric.split("\\\\n");
            StringBuilder snippet = new StringBuilder();
            int count = 0;
            for (String line : lines) {
                String clean = line.replaceAll("\\[.*?\\]", "").trim();
                if (!clean.isEmpty()) {
                    snippet.append(clean).append(" / ");
                    count++;
                    if (count >= 3) break;
                }
            }
            if (snippet.isEmpty()) return null;
            String result = snippet.substring(0, snippet.length() - 2);
            if (result.length() > 80) result = result.substring(0, 80) + "…";
            return result;
        } catch (Exception e) {
            log.debug("获取QQ歌词失败 songmid={}", songmid);
            return null;
        }
    }

    private String extractQqArtist(JsonNode singer) {
        if (!singer.isArray() || singer.isEmpty()) return "未知";
        StringBuilder sb = new StringBuilder();
        for (JsonNode s : singer) {
            sb.append(s.path("name").asText()).append("/");
        }
        return sb.substring(0, sb.length() - 1);
    }
}
