package com.example.youkeda_project.tool.life;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.tool.ToolResult;
import com.example.youkeda_project.service.assist.MusicService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 音乐工具：search(按歌名/歌手搜歌) 与 recommend(按风格/心情荐歌)。
 */

@Component
public class MusicTool extends AbstractTool {

        private final MusicService musicService;

    public MusicTool(MusicService musicService) {
        this.musicService = musicService;
    }

    @Override
    public String getName() { return "music"; }

    @Override
    public String getDescription() {
        return "音乐服务：operation=search 按歌名/歌手搜索歌曲(返回播放链接与歌词)，"
            + "operation=recommend 按风格/心情/场景推荐歌曲。"
            + "用户提到具体歌名/歌手想听歌时用 search；用户说'推荐一些歌''有什么好听的歌''推荐古风歌曲'等用 recommend。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operation", Map.of("type", "string",
            "enum", java.util.List.of("search", "recommend"), "description", "操作类型"));
        properties.put("keyword", Map.of("type", "string", "description", "歌曲关键词，如'周杰伦 晴天'（search需要）"));
        properties.put("description", Map.of("type", "string", "description", "对歌曲风格的描述，如'古风''伤感''适合跑步听'（recommend需要）"));
        params.put("properties", properties);
        params.put("required", java.util.List.of("operation"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String op = arguments != null ? (String) arguments.get("operation") : null;
        if (op == null) return ToolResult.text("错误: 缺少操作类型(operation)，应为 search/recommend");
        return switch (op) {
            case "search" -> {
                String keyword = arguments.get("keyword") instanceof String k ? k : "";
                log.info("MusicTool search: keyword={}", keyword);
                yield ToolResult.text(musicService.search(keyword));
            }
            case "recommend" -> {
                String description = arguments.get("description") instanceof String d ? d : "";
                log.info("MusicTool recommend: description={}", description);
                yield ToolResult.text(musicService.recommend(description));
            }
            default -> ToolResult.text("错误: 未知操作 " + op);
        };
    }
}
