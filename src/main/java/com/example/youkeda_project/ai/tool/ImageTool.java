package com.example.youkeda_project.ai.tool;

import com.example.youkeda_project.ai.Tool;
import com.example.youkeda_project.ai.ToolResult;
import com.example.youkeda_project.service.wechat.ImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图片生成工具(Function Calling).
 * <p>
 * 定义 generate_image 函数,供 DeepSeek 通过 Function Calling 调用.
 * 委托 ImageService 调用通义万相生图,返回 OSS 图片 URL.
 */
@Component
public class ImageTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ImageTool.class);

    private final ImageService imageService;

    public ImageTool(ImageService imageService) {
        this.imageService = imageService;
    }

    @Override
    public String getName() {
        return "generate_image";
    }

    @Override
    public String getDescription() {
        return "根据文字描述生成图片,返回图片下载地址";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> prompt = new LinkedHashMap<>();
        prompt.put("type", "string");
        prompt.put("description", "图片的文字描述,越详细越好,如:一只橘猫坐在窗台上晒太阳");
        properties.put("prompt", prompt);

        params.put("properties", properties);
        params.put("required", java.util.List.of("prompt"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String prompt = arguments != null ? (String) arguments.get("prompt") : null;
        if (prompt == null || prompt.isBlank()) {
            return ToolResult.text("错误: 请提供图片描述");
        }
        log.info("ImageTool 执行: prompt={}", prompt);

        String result = imageService.generateImage(prompt);
        if (result.startsWith("http://") || result.startsWith("https://")) {
            log.info("图片生成成功: {}", result);
            return ToolResult.image("图片已生成,地址: " + result, result);
        }
        log.warn("图片生成失败: {}", result);
        return ToolResult.text("图片生成失败: " + result);
    }
}
