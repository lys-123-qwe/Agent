package com.example.youkeda_project.service;

import com.example.youkeda_project.config.ApiConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);
    private static final String MODEL = "wan2.6-t2i";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ApiConfig apiConfig;

    public ImageService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public String generateImage(String prompt) {
        if (apiConfig.getImageApiKey() == null || apiConfig.getImageApiKey().isBlank()) {
            return "错误: 未配置通义万相 API Key，请在 application.properties 中设置 image.api.key";
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            return "错误: 请提供图片描述，例如: 图片生成 一只猫";
        }

        try {
            Map<String, Object> requestBody = Map.of(
                "model", MODEL,
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
                    "size", "1280*1280"
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

            String responseJson = new String(responseBytes, StandardCharsets.UTF_8);
            log.info("通义万相响应: {}", responseJson);

            return parseImageResponse(responseJson);

        } catch (Exception e) {
            log.error("通义万相调用失败", e);
            return "图片生成失败: " + e.getMessage();
        }
    }

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

    private String parseImageResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

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
            return "错误: 无法解析图片URL，原始响应: " + json;

        } catch (Exception e) {
            log.error("解析通义万相响应失败", e);
            return "错误: 解析响应失败 - " + e.getMessage();
        }
    }
}
