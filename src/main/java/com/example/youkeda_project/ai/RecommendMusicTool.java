package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.MusicService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RecommendMusicTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(RecommendMusicTool.class);

    private final MusicService musicService;

    public RecommendMusicTool(MusicService musicService) {
        this.musicService = musicService;
    }

    @Override
    public String getName() {
        return "recommend_music";
    }

    @Override
    public String getDescription() {
        return "【推荐歌曲】根据风格、心情、场景推荐歌曲。用户说'推荐一些歌''有什么好听的歌''推荐古风歌曲''推荐适合跑步听的歌'时用此工具。搜指定歌曲/歌手请用 play_song。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> description = new LinkedHashMap<>();
        description.put("type", "string");
        description.put("description", "用户对歌曲的描述，如'古风'、'伤感'、'周杰伦'、'适合跑步听'、'最近热门'等");
        properties.put("description", description);

        params.put("properties", properties);
        params.put("required", java.util.List.of("description"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String description = "";
        if (arguments != null && arguments.get("description") instanceof String d) {
            description = d;
        }
        log.info("RecommendMusicTool 执行: description={}", description);
        return ToolResult.text(musicService.recommend(description));
    }
}
