package com.example.youkeda_project.ai.tool;

import com.example.youkeda_project.ai.AIService;
import com.example.youkeda_project.ai.Tool;
import com.example.youkeda_project.ai.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public class RecognizeImageTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(RecognizeImageTool.class);

    private final AIService aiService;
    private final String userId;
    private final byte[] imageBytes;

    public RecognizeImageTool(AIService aiService, String userId, byte[] imageBytes) {
        this.aiService = aiService;
        this.userId = userId;
        this.imageBytes = imageBytes;
    }

    @Override
    public String getName() {
        return "recognize_image";
    }

    @Override
    public String getDescription() {
        return "识别已上传图片的内容,回答关于图片的问题.适用于:这是什么,图片里有什么,描述图片,提取图片中的文字等场景";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> prompt = new LinkedHashMap<>();
        prompt.put("type", "string");
        prompt.put("description", "对图片的提问或识别要求,如:这张图里有什么,请描述这张图片,提取图中的文字.默认为'请描述这张图片'");
        properties.put("prompt", prompt);

        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String prompt = arguments != null ? (String) arguments.get("prompt") : null;
        if (prompt == null || prompt.isBlank()) {
            prompt = "请描述这张图片";
        }
        log.info("RecognizeImageTool 执行: prompt={}", prompt);

        String reply = aiService.chatWithImage(userId + "__vision__", prompt, imageBytes);
        return ToolResult.text(reply);
    }
}
