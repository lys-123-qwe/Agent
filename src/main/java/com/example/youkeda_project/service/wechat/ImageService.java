package com.example.youkeda_project.service.wechat;

import com.example.youkeda_project.config.ApiConfig;
import com.example.youkeda_project.util.MimeTypes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 通义万相图片生成服务.
 * <p>
 * 调用阿里云通义万相 wan2.6-t2i 模型生图,返回图片下载 URL,
 * 并提供下载图片字节流的能力(供后续 CDN 上传发送到微信).
 */

@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ApiConfig apiConfig;

    public ImageService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    /**
     * 调用通义万相生图 API,返回图片 URL 或错误信息.
     * wanx 系列模型走异步 text2image 任务接口;wan2.x 系列走同步 multimodal-generation 接口.
     */
    public String generateImage(String prompt) {
        if (apiConfig.getImageApiKey() == null || apiConfig.getImageApiKey().isBlank()) {
            return "错误: 未配置通义万相 API Key,请在 application.properties 中设置 image.api.key";
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            return "错误: 请提供图片描述,例如: 图片生成 一只猫";
        }
        String model = apiConfig.getImageModel();
        if (model != null && model.toLowerCase().contains("wanx")) {
            return generateImageAsync(model, prompt);
        }
        return generateImageSync(prompt);
    }

    /**
     * wanx 系列：异步 text2image 任务接口。
     * 1) 提交任务获取 task_id；2) 轮询任务状态直到 SUCCEEDED；3) 取结果图片 URL。
     */
    private String generateImageAsync(String model, String prompt) {
        try {
            Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", Map.of("prompt", prompt.trim()),
                "parameters", Map.of(
                    "prompt_extend", true,
                    "watermark", false,
                    "n", 1,
                    "size", apiConfig.getImageGenerateSize()
                )
            );

            String body = objectMapper.writeValueAsString(requestBody);
            log.info("通义万相异步生图提交: {}", body);

            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            byte[] respBytes = restClient.post()
                    .uri(apiConfig.getImageApiUrl() + "/api/v1/services/aigc/text2image/image-synthesis")
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + apiConfig.getImageApiKey())
                    .header("X-DashScope-Async", "enable")
                    .body(bodyBytes)
                    .retrieve()
                    .body(byte[].class);

            if (respBytes == null) {
                return "错误: AI 服务返回空响应";
            }
            String respJson = new String(respBytes, StandardCharsets.UTF_8);
            log.info("通义万相异步生图提交响应: {}", respJson);

            JsonNode root = objectMapper.readTree(respJson);
            String taskId = root.path("output").path("task_id").asText(null);
            if (taskId == null || taskId.isBlank()) {
                return "错误: 未获取到任务ID, 原始响应: " + respJson;
            }

            // 轮询任务状态，最多 60 秒
            String taskUrl = apiConfig.getImageApiUrl() + "/api/v1/tasks/" + taskId;
            for (int i = 0; i < 30; i++) {
                Thread.sleep(2000);
                byte[] pollBytes = restClient.get()
                        .uri(taskUrl)
                        .header("Authorization", "Bearer " + apiConfig.getImageApiKey())
                        .retrieve()
                        .body(byte[].class);
                if (pollBytes == null) continue;

                JsonNode poll = objectMapper.readTree(new String(pollBytes, StandardCharsets.UTF_8));
                String status = poll.path("output").path("task_status").asText("");
                log.info("生图任务轮询[{}] status={}", i, status);
                if ("SUCCEEDED".equals(status)) {
                    JsonNode results = poll.path("output").path("results");
                    if (results.isArray() && !results.isEmpty()) {
                        String url = results.get(0).path("url").asText(null);
                        if (url != null && !url.isBlank()) {
                            com.example.youkeda_project.util.TokenStats.recordImage("文生图");
                            return url;
                        }
                    }
                    return "错误: 任务成功但未解析到图片URL: " + poll;
                }
                if ("FAILED".equals(status) || "CANCELED".equals(status)) {
                    return "错误: 生图任务" + status + ": " + poll;
                }
            }
            return "错误: 生图任务超时（60秒）";
        } catch (Exception e) {
            log.error("通义万相异步生图调用失败", e);
            return "图片生成失败: " + e.getMessage();
        }
    }

    /**
     * wan2.x 系列：同步 multimodal-generation 接口。
     * 请求使用 byte[] + UTF-8 编解码避免乱码。
     */
    private String generateImageSync(String prompt) {
        try {
            // 通义万相请求体:model + input.messages[{role, content[{text}]}] + parameters
            Map<String, Object> requestBody = Map.of(
                "model", apiConfig.getImageModel(),
                "input", Map.of("messages", java.util.List.of(
                    Map.of("role", "user",
                        "content", java.util.List.of(
                            Map.of("text", prompt.trim())
                        ))
                )),
                "parameters", Map.of(
                    "prompt_extend", true,
                    "watermark", false,
                    "n", 1,
                    "size", apiConfig.getImageGenerateSize()
                )
            );

            String body = objectMapper.writeValueAsString(requestBody);
            log.info("通义万相请求: {}", body);

            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            byte[] responseBytes = restClient.post()
                    .uri(apiConfig.getImageApiUrl() + "/api/v1/services/aigc/multimodal-generation/generation")
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + apiConfig.getImageApiKey())
                    .body(bodyBytes)
                    .retrieve()
                    .body(byte[].class);

            if (responseBytes == null) {
                return "错误: AI 服务返回空响应";
            }
            String responseJson = new String(responseBytes, StandardCharsets.UTF_8);
            log.info("通义万相响应: {}", responseJson);

            String result = parseImageResponse(responseJson);
            if (result != null && !result.startsWith("错误:")) {
                com.example.youkeda_project.util.TokenStats.recordImage("文生图");
            }
            return result;

        } catch (Exception e) {
            log.error("通义万相调用失败", e);
            return "图片生成失败: " + e.getMessage();
        }
    }

    /**
     * 通义万相图生图编辑(wan2.6-image).
     * 传入参考图片字节 + 文字描述,返回编辑后的图片 URL.
     */
    public String editImage(byte[] refImage, String prompt) {
        if (apiConfig.getImageApiKey() == null || apiConfig.getImageApiKey().isBlank()) {
            return "错误: 未配置通义万相 API Key";
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            return "错误: 请提供编辑描述";
        }
        if (refImage == null || refImage.length == 0) {
            return "错误: 参考图片数据为空";
        }

        try {
            String mimeType = MimeTypes.detect(refImage);
            String base64 = Base64.getEncoder().encodeToString(refImage);
            String dataUrl = "data:" + mimeType + ";base64," + base64;

            Map<String, Object> requestBody = Map.of(
                "model", apiConfig.getImageEditModel(),
                "input", Map.of("messages", java.util.List.of(
                    Map.of("role", "user",
                        "content", java.util.List.of(
                            Map.of("text", prompt.trim()),
                            Map.of("image", dataUrl)
                        ))
                )),
                "parameters", Map.of(
                    "n", 1,
                    "size", apiConfig.getImageEditSize()
                )
            );

            String body = objectMapper.writeValueAsString(requestBody);
            log.info("通义万相图编辑请求: {}", body);

            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            byte[] responseBytes = restClient.post()
                    .uri(apiConfig.getImageApiUrl() + "/api/v1/services/aigc/multimodal-generation/generation")
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + apiConfig.getImageApiKey())
                    .body(bodyBytes)
                    .retrieve()
                    .body(byte[].class);

            if (responseBytes == null) {
                return "错误: AI 服务返回空响应";
            }
            String responseJson = new String(responseBytes, StandardCharsets.UTF_8);
            log.info("通义万相图编辑响应: {}", responseJson);

            String result = parseImageResponse(responseJson);
            if (result != null && !result.startsWith("错误:")) {
                com.example.youkeda_project.util.TokenStats.recordImage("图编辑");
            }
            return result;

        } catch (Exception e) {
            log.error("通义万相图编辑调用失败", e);
            return "图片编辑失败: " + e.getMessage();
        }
    }

    /**
     * 从图片 URL 下载原始字节流,用于后续 AES 加密上传到微信 CDN.
     */
    public byte[] downloadImage(String url) {
        log.info("下载图片: {}", url);
        ResponseEntity<byte[]> resp = restClient.get()
            .uri(URI.create(url))
            .retrieve()
            .toEntity(byte[].class);
        byte[] bytes = resp.getBody();
        log.info("图片下载完成: {} bytes", bytes != null ? bytes.length : 0);
        return bytes;
    }

    /**
     * 解析通义万相响应 JSON,提取图片 URL.
     * 兼容两种响应格式:
     * - 新版: output.choices[0].message.content[{image: "..."}]
     * - 旧版: output.results[0].image
     */
    private String parseImageResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // 新版响应格式: output.choices[0].message.content[{image: "..."}]
            JsonNode choices = root.path("output").path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode content = choices.get(0).path("message").path("content");
                if (content.isArray() && !content.isEmpty()) {
                    JsonNode first = content.get(0);
                    if (first.has("image")) {
                        String url = first.path("image").asText();
                        if (!url.isBlank()) return url;
                    }
                    if (first.has("image_url")) {
                        String url = first.path("image_url").asText();
                        if (!url.isBlank()) return url;
                    }
                }
                if (content.isTextual()) {
                    return content.asText();
                }
            }

            // 旧版响应格式: output.results[0].image
            JsonNode output = root.path("output");
            if (output.has("results") && output.path("results").isArray()) {
                JsonNode first = output.path("results").get(0);
                if (first.has("image")) {
                    String url = first.path("image").asText();
                    if (!url.isBlank()) return url;
                }
                if (first.has("image_url")) {
                    String url = first.path("image_url").asText();
                    if (!url.isBlank()) return url;
                }
            }

            log.warn("无法从响应中解析图片URL: {}", json);
            return "错误: 无法解析图片URL,原始响应: " + json;

        } catch (Exception e) {
            log.error("解析通义万相响应失败", e);
            return "错误: 解析响应失败 - " + e.getMessage();
        }
    }
}
