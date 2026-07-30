package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.MusicService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MusicTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(MusicTool.class);

    private final MusicService musicService;

    public MusicTool(MusicService musicService) {
        this.musicService = musicService;
    }

    @Override
    public String getName() {
        return "play_song";
    }

    @Override
    public String getDescription() {
        return "[找歌/听歌/查歌词]搜索歌曲,返回歌名,歌手,专辑,歌词片段和网易云/QQ音乐播放链接.用户提到任何歌名,歌手,想听歌时,必须用此工具,不要自己编造.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "歌曲关键词,如'周杰伦 晴天','一路生花'等");
        properties.put("keyword", keyword);

        params.put("properties", properties);
        params.put("required", java.util.List.of("keyword"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String keyword = "";
        if (arguments != null && arguments.get("keyword") instanceof String k) {
            keyword = k;
        }
        log.info("MusicTool 执行: keyword={}", keyword);
        return ToolResult.text(musicService.search(keyword));
    }
}
