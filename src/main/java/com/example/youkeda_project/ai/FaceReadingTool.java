package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.FortuneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public class FaceReadingTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(FaceReadingTool.class);

    private final FortuneService fortuneService;
    private final byte[] imageBytes;

    public FaceReadingTool(FortuneService fortuneService, byte[] imageBytes) {
        this.fortuneService = fortuneService;
        this.imageBytes = imageBytes;
    }

    @Override
    public String getName() { return "face_reading"; }

    @Override
    public String getDescription() {
        return "【面相分析】分析已上传照片的面相，根据五官/脸型/气色分析性格、运势和健康。适用于：看面相、分析面相、看脸相。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> p = new LinkedHashMap<>();
        Map<String, Object> prompt = new LinkedHashMap<>();
        prompt.put("type", "string");
        prompt.put("description", "分析要求，如：看面相、分析运势、看看我的财运。默认为'请全面分析我的面相'");
        p.put("prompt", prompt);
        params.put("properties", p);
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String prompt = arguments != null ? (String) arguments.get("prompt") : null;
        if (prompt == null || prompt.isBlank()) {
            prompt = "请全面分析我的面相";
        }
        log.info("FaceReadingTool 执行: prompt={}", prompt);
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String result = fortuneService.faceReading(base64, prompt);
        return ToolResult.text(result);
    }
}
