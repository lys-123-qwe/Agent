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
    private static final String SEARCH_API = "https://music.163.com/api/search/get/web";
    private static final String LYRIC_API = "https://music.163.com/api/song/lyric";
    private static final int MAX_SONGS = 5;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MusicService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .defaultHeader("Referer", "https://music.163.com")
                .build();
    }

    public String search(String keyword) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = SEARCH_API + "?s=" + encoded + "&type=1&offset=0&total=true&limit=" + MAX_SONGS;
            log.info("搜索歌曲: {}", url);

            String json = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(json);
            int code = root.path("code").asInt();
            if (code != 200) {
                return "搜索失败: code=" + code;
            }

            JsonNode songs = root.path("result").path("songs");
            if (!songs.isArray() || songs.isEmpty()) {
                return "没有找到相关歌曲";
            }

            StringBuilder sb = new StringBuilder("🎵 找到以下歌曲：\n\n");
            for (int i = 0; i < songs.size(); i++) {
                appendSongInfo(sb, songs.get(i), i + 1);
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("搜索歌曲失败", e);
            return "搜索歌曲失败: " + e.getMessage();
        }
    }

    public String recommend(String description) {
        try {
            String[] keywords;
            String desc = description.trim().toLowerCase();
            if (desc.matches(".*(歌|曲|音乐|听).*")) {
                keywords = new String[]{description, description + " 热门", description + " 经典"};
            } else {
                keywords = new String[]{description + " 歌曲", description + " 音乐", description + " 热门"};
            }

            java.util.LinkedHashSet<Long> seenIds = new java.util.LinkedHashSet<>();
            java.util.ArrayList<JsonNode> allSongs = new java.util.ArrayList<>();

            for (String kw : keywords) {
                String encoded = URLEncoder.encode(kw, StandardCharsets.UTF_8);
                String url = SEARCH_API + "?s=" + encoded + "&type=1&offset=0&total=true&limit=10";
                log.info("推荐搜索: {}", url);

                String json = restClient.get().uri(url).retrieve().body(String.class);
                JsonNode root = objectMapper.readTree(json);
                if (root.path("code").asInt() != 200) continue;

                JsonNode songs = root.path("result").path("songs");
                if (!songs.isArray()) continue;

                for (JsonNode s : songs) {
                    long id = s.path("id").asLong();
                    if (seenIds.add(id)) {
                        allSongs.add(s);
                        if (allSongs.size() >= 10) break;
                    }
                }
                if (allSongs.size() >= 10) break;
            }

            if (allSongs.isEmpty()) {
                return "没有找到相关推荐";
            }

            StringBuilder sb = new StringBuilder("🎵 为你推荐 ").append(description).append(" 相关歌曲：\n\n");
            for (int i = 0; i < allSongs.size(); i++) {
                appendSongInfo(sb, allSongs.get(i), i + 1);
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("推荐歌曲失败", e);
            return "推荐歌曲失败: " + e.getMessage();
        }
    }

    private void appendSongInfo(StringBuilder sb, JsonNode song, int idx) {
        long id = song.path("id").asLong();
        String name = song.path("name").asText("未知");
        String artist = extractArtists(song.path("artists"));
        String album = song.path("album").path("name").asText("");
        long durationMs = song.path("duration").asLong();
        String duration = formatDuration(durationMs);

        String songNameEncoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
        sb.append(idx).append(". ").append(name)
                .append(" - ").append(artist).append("\n");
        if (!album.isBlank()) sb.append("   专辑: ").append(album).append("\n");
        sb.append("   时长: ").append(duration).append("\n");
        sb.append("   ▶ 网易云听歌: https://music.163.com/m/song?id=").append(id).append("\n");
        sb.append("   ▶ QQ音乐搜索: https://y.qq.com/n/ryqq/search?w=").append(songNameEncoded).append("\n");

        String lyricSnippet = fetchLyricSnippet(id);
        if (lyricSnippet != null) {
            sb.append("   📝 ").append(lyricSnippet).append("\n");
        }
        sb.append("\n");
    }

    private String fetchLyricSnippet(long songId) {
        try {
            String url = LYRIC_API + "?id=" + songId + "&lv=-1&kv=-1&tv=-1";
            String json = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode lrc = root.path("lrc").path("lyric");
            if (lrc.isMissingNode() || lrc.asText().isBlank()) return null;

            String lyric = lrc.asText();
            String[] lines = lyric.split("\n");
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
            String result = snippet.substring(0, snippet.length() - 2).toString();
            if (result.length() > 80) result = result.substring(0, 80) + "…";
            return result;
        } catch (Exception e) {
            log.debug("获取歌词失败 songId={}", songId);
            return null;
        }
    }

    private String extractArtists(JsonNode artists) {
        if (!artists.isArray() || artists.isEmpty()) return "未知";
        StringBuilder sb = new StringBuilder();
        for (JsonNode a : artists) {
            sb.append(a.path("name").asText()).append("/");
        }
        return sb.substring(0, sb.length() - 1);
    }

    private String formatDuration(long ms) {
        long totalSec = ms / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format("%d:%02d", min, sec);
    }
}
