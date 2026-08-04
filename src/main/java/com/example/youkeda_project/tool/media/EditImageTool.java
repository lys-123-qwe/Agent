package com.example.youkeda_project.tool.media;

import com.example.youkeda_project.tool.AbstractTool;

import com.example.youkeda_project.tool.ToolResult;
import com.example.youkeda_project.service.wechat.ImageService;

import java.util.LinkedHashMap;
import java.util.Map;

public class EditImageTool extends AbstractTool {

        private final ImageService imageService;
    private final byte[] imageBytes;

    public EditImageTool(ImageService imageService, byte[] imageBytes) {
        this.imageService = imageService;
        this.imageBytes = imageBytes;
    }

    @Override
    public String getName() {
        return "edit_image";
    }

    @Override
    public String getDescription() {
        return "根据文字描述编辑已上传的图片,如修改物体,替换元素,调整颜色或风格,改变背景等,返回编辑后的图片";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> prompt = new LinkedHashMap<>();
        prompt.put("type", "string");
        prompt.put("description", "编辑描述,如:把猫换成狗,让背景变成蓝色,给图片添加文字'Hello World',改为油画风格");
        properties.put("prompt", prompt);

        params.put("properties", properties);
        params.put("required", java.util.List.of("prompt"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String prompt = arguments != null ? (String) arguments.get("prompt") : null;
        if (prompt == null || prompt.isBlank()) {
            return ToolResult.text("错误: 请提供编辑描述");
        }
        log.info("EditImageTool 执行: prompt={}", prompt);

        String result = imageService.editImage(imageBytes, prompt);
        if (result.startsWith("http://") || result.startsWith("https://")) {
            log.info("图片编辑成功: {}", result);
            return ToolResult.image("图片已编辑完成", result);
        }
        log.warn("图片编辑失败: {}", result);
        return ToolResult.text("图片编辑失败: " + result);
    }
}
