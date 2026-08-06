package com.example.youkeda_project.tool.life;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.tool.ToolResult;
import com.example.youkeda_project.service.assist.HotCommentService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网易云热评工具(Function Calling).
 * <p>
 * 定义 get_hot_comment 函数,委托 HotCommentService 获取热评.
 * 支持随机热评和按歌曲名搜索.
 */

@Component
public class HotCommentTool extends AbstractTool {

        private final HotCommentService hotCommentService;

    public HotCommentTool(HotCommentService hotCommentService) {
        this.hotCommentService = hotCommentService;
    }

    @Override
    public String getName() {
        return "get_hot_comment";
    }

    @Override
    public String getDescription() {
        return "获取网易云音乐热门评论.当用户说想听热评,网易云热评,来条评论时调用."
            + "如果用户提到了具体歌曲名(如'晴天的热评','周杰伦稻香的热评'),必须传入 song 参数.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> song = new LinkedHashMap<>();
        song.put("type", "string");
        song.put("description", "歌曲名或歌手+歌名,如'晴天','周杰伦 稻香'."
            + "用户没提具体歌名就不要传这个参数,会随机返回一条热评.");
        properties.put("song", song);

        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String song = arguments != null ? (String) arguments.get("song") : null;

        if (song != null && !song.isBlank()) {
            log.info("HotCommentTool 执行: song={}", song);
            try {
                return ToolResult.text(hotCommentService.searchComment(song));
            } catch (Exception e) {
                log.error("搜索歌曲热评失败: {}", e.getMessage());
                return ToolResult.text("获取热评失败: " + e.getMessage());
            }
        }

        log.info("HotCommentTool 执行: 随机");
        try {
            return ToolResult.text(hotCommentService.getRandomComment());
        } catch (Exception e) {
            log.error("获取随机热评失败: {}", e.getMessage());
            return ToolResult.text("获取热评失败: " + e.getMessage());
        }
    }
}
