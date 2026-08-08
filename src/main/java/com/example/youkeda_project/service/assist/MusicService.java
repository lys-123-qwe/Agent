package com.example.youkeda_project.service.assist;

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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/** 音乐搜索与推荐（QQ 音乐）。 */


@Service
public class MusicService {

    private static final Logger log = LoggerFactory.getLogger(MusicService.class);
    private static final String QQ_SEARCH_API = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    private static final String QQ_LYRIC_API = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg";
    private static final String NETEASE_SEARCH_API = "https://music.163.com/api/search/get/web";
    private static final int MAX_SONGS = 5;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MusicService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String search(String keyword) {
        List<SongInfo> qqList = qqSearch(keyword, MAX_SONGS);
        List<SongInfo> neteaseList = neteaseSearch(keyword, MAX_SONGS);

        if (qqList.isEmpty() && neteaseList.isEmpty()) {
            return "没有找到相关歌曲";
        }

        Map<String, SongInfo> merged = new LinkedHashMap<>();
        for (SongInfo s : qqList) merged.putIfAbsent(s.key(), s);
        for (SongInfo s : neteaseList) {
            merged.merge(s.key(), s, SongInfo::merge);
        }

        SongInfo best = withLyric(merged.values().iterator().next());
        StringBuilder sb = new StringBuilder();
        sb.append("🎵 ").append(best.name).append(" - ").append(best.artist).append("\n");
        if (!best.duration.isBlank()) sb.append("   时长: ").append(best.duration).append("\n");
        if (!best.lyric.isBlank()) {
            sb.append("   歌词:\n");
            for (String l : best.lyric.split("\n")) {
                if (!l.isBlank()) sb.append("     ").append(l).append("\n");
            }
        }
        if (!best.qqSongmid.isBlank()) {
            sb.append("   ▶ 播放: https://i.y.qq.com/v8/playsong.html?songmid=").append(best.qqSongmid).append("&source=wechat&ADTAG=wechat\n");
        } else if (best.neteaseId > 0) {
            sb.append("   ▶ 播放: https://music.163.com/song?id=").append(best.neteaseId).append("\n");
        }
        return sb.toString();
    }

    public String recommend(String description) {
        String[] keywords;
        String desc = description.trim().toLowerCase();
        if (desc.matches(".*(歌|曲|音乐|听).*")) {
            keywords = new String[]{description, description + " 热门", description + " 经典"};
        } else {
            keywords = new String[]{description + " 歌曲", description + " 音乐", description + " 热门"};
        }

        Map<String, SongInfo> merged = new LinkedHashMap<>();
        for (String kw : keywords) {
            for (SongInfo s : qqSearch(kw, MAX_SONGS)) {
                merged.putIfAbsent(s.key(), s);
            }
            if (merged.size() >= MAX_SONGS) break;
        }

        if (merged.isEmpty()) {
            return "没有找到相关推荐";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🎵 为你推荐 ").append(description).append(" 相关歌曲:\n\n");
        int idx = 1;
        for (SongInfo s : merged.values()) {
            SongInfo d = withLyric(s);
            sb.append(idx).append(". ").append(d.name).append(" - ").append(d.artist).append("\n");
            if (!d.album.isBlank()) sb.append("   专辑: ").append(d.album).append("\n");
            if (!d.duration.isBlank()) sb.append("   时长: ").append(d.duration).append("\n");
            if (!d.qqSongmid.isBlank()) {
                sb.append("   ▶ QQ音乐: https://i.y.qq.com/v8/playsong.html?songmid=").append(d.qqSongmid).append("&source=wechat&ADTAG=wechat\n");
            }
            if (d.neteaseId > 0) {
                sb.append("   ▶ 网易云: https://music.163.com/song?id=").append(d.neteaseId).append("\n");
            }
            if (!d.lyric.isBlank()) {
                sb.append("   歌词:\n");
                for (String l : d.lyric.split("\n")) {
                    if (!l.isBlank()) sb.append("     ").append(l).append("\n");
                }
            }
            sb.append("\n");
            idx++;
        }
        return sb.toString();
    }

    private List<SongInfo> qqSearch(String keyword, int limit) {
        List<SongInfo> result = qqSearchInner(keyword, limit * 2);
        if (!result.isEmpty()) return trim(result, limit);
        result = qqSearchInner(keyword + " 原唱", limit * 2);
        if (!result.isEmpty()) return trim(result, limit);
        return List.of();
    }

    private List<SongInfo> qqSearchInner(String keyword, int fetchSize) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = QQ_SEARCH_API + "?w=" + encoded + "&format=json&n=" + fetchSize;
            log.info("QQ音乐搜索: {}", url);

            String json = httpGet(url, "https://y.qq.com");
            JsonNode root = objectMapper.readTree(json);
            if (root.path("code").asInt() != 0) return List.of();

            JsonNode list = root.path("data").path("song").path("list");
            if (!list.isArray() || list.isEmpty()) return List.of();

            List<SongInfo> result = new ArrayList<>();
            for (JsonNode s : list) {
                String name = s.path("songname").asText("");
                if (name.isEmpty()) name = s.path("name").asText("");
                String songmid = s.path("songmid").asText("");
                if (songmid.isEmpty()) continue;
                String artist = extractQqArtist(s.path("singer"));
                String album = s.path("albumname").asText("");
                if (album.isEmpty()) album = s.path("album").path("name").asText("");
                int seconds = s.path("interval").asInt(0);
                String duration = seconds > 0 ? String.format("%d:%02d", seconds / 60, seconds % 60) : "";
                result.add(new SongInfo(name, artist, album, duration, songmid, 0, ""));
            }
            return result;
        } catch (Exception e) {
            log.warn("QQ音乐搜索异常", e);
            return List.of();
        }
    }

    private List<SongInfo> neteaseSearch(String keyword, int limit) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = NETEASE_SEARCH_API + "?s=" + encoded + "&type=1&offset=0&total=true&limit=" + limit * 2;
            log.info("网易云搜索: {}", url);

            String json = httpGet(url, "https://music.163.com");
            JsonNode root = objectMapper.readTree(json);
            if (root.path("code").asInt() != 200) return List.of();

            JsonNode songs = root.path("result").path("songs");
            if (!songs.isArray()) return List.of();

            List<SongInfo> result = new ArrayList<>();
            for (JsonNode s : songs) {
                String name = s.path("name").asText("");
                long id = s.path("id").asLong();
                if (id <= 0) continue;
                String artist = extractNeteaseArtist(s.path("artists"));
                String album = s.path("album").path("name").asText("");
                int seconds = s.path("duration").asInt(0) / 1000;
                String duration = seconds > 0 ? String.format("%d:%02d", seconds / 60, seconds % 60) : "";
                result.add(new SongInfo(name, artist, album, duration, "", id, ""));
                if (result.size() >= limit) break;
            }
            return result;
        } catch (Exception e) {
            log.warn("网易云搜索异常", e);
            return List.of();
        }
    }

    private String httpGet(String url, String referer) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                .header("Referer", referer)
                .header("Accept", "application/json")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    private List<SongInfo> trim(List<SongInfo> list, int max) {
        return list.size() > max ? list.subList(0, max) : list;
    }

    private String fetchQqLyric(String songmid) {
        try {
            String url = QQ_LYRIC_API + "?songmid=" + songmid + "&format=json";
            String json = httpGet(url, "https://y.qq.com");
            JsonNode root = objectMapper.readTree(json);
            if (root.path("code").asInt() != 0) return "";

            String lyric = root.path("lyric").asText();
            if (lyric.isBlank()) return "";

            String[] lines = lyric.split("\\\\n");
            StringBuilder snippet = new StringBuilder();
            int count = 0;
            for (String line : lines) {
                String clean = line.replaceAll("\\[.*?\\]", "").trim();
                if (!clean.isEmpty()) {
                    snippet.append(clean).append("\n");
                    count++;
                    if (count >= 4) break;
                }
            }
            return snippet.toString();
        } catch (Exception e) {
            log.debug("获取QQ歌词失败 songmid={}", songmid);
            return "";
        }
    }

    /** 懒加载歌词：仅当歌曲最终需要展示时才拉取 QQ 歌词。 */
    private SongInfo withLyric(SongInfo s) {
        if (!s.lyric.isBlank() || s.qqSongmid.isBlank()) return s;
        return new SongInfo(s.name, s.artist, s.album, s.duration,
            s.qqSongmid, s.neteaseId, fetchQqLyric(s.qqSongmid));
    }

    private String extractQqArtist(JsonNode singer) {
        if (!singer.isArray() || singer.isEmpty()) return "未知";
        StringBuilder sb = new StringBuilder();
        for (JsonNode s : singer) {
            sb.append(s.path("name").asText()).append("/");
        }
        return sb.substring(0, sb.length() - 1);
    }

    private String extractNeteaseArtist(JsonNode artists) {
        if (!artists.isArray() || artists.isEmpty()) return "未知";
        StringBuilder sb = new StringBuilder();
        for (JsonNode a : artists) {
            sb.append(a.path("name").asText()).append("/");
        }
        return sb.substring(0, sb.length() - 1);
    }

    private record SongInfo(
        String name, String artist, String album, String duration,
        String qqSongmid, long neteaseId, String lyric
    ) {
        String key() { return (name + " - " + artist).toLowerCase(); }

        SongInfo merge(SongInfo other) {
            return new SongInfo(
                name, artist, album, duration,
                qqSongmid.isEmpty() ? other.qqSongmid : qqSongmid,
                neteaseId <= 0 ? other.neteaseId : neteaseId,
                lyric.isEmpty() ? other.lyric : lyric
            );
        }
    }
}
