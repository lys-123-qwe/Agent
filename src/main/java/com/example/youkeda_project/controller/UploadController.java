package com.example.youkeda_project.controller;

import com.example.youkeda_project.ai.AIService;
import com.example.youkeda_project.service.wechat.FileService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Web 端图片/文件上传接口：供前端聊天页发送图片识别、文件内容提取。
 */
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private final AIService aiService;
    private final FileService fileService;

    public UploadController(AIService aiService, FileService fileService) {
        this.aiService = aiService;
        this.fileService = fileService;
    }

    /** 上传图片并识别（qwen-vl-plus）。 */
    @PostMapping("/image")
    public Map<String, Object> image(@RequestParam(defaultValue = "web") String userId,
                                     @RequestParam("file") MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        String text = aiService.chatWithImage(userId, "请识别这张图片的内容", bytes);
        return Map.of("ok", true, "text", text);
    }

    /** 上传文件：提取文本 + 提取内嵌图片识别（docx/pptx/xlsx），文字与图片结果拼接。 */
    @PostMapping("/file")
    public Map<String, Object> file(@RequestParam(defaultValue = "web") String userId,
                                    @RequestParam("file") MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        byte[] bytes = file.getBytes();
        String text = fileService.extractText(bytes, filename);

        boolean noText = text == null || text.isBlank()
            || text.startsWith("文件解析失败") || text.startsWith("文本读取失败")
            || "(文件内容为空)".equals(text) || text.contains("未能从文件中提取");

        StringBuilder sb = new StringBuilder();
        if (!noText) sb.append(text);

        // 提取内嵌图片并识别（无论有无文字）
        List<byte[]> images = fileService.extractImages(bytes, filename);
        if (!images.isEmpty()) {
            int limit = Math.min(images.size(), 5);
            if (sb.length() > 0) sb.append("\n\n--- 文档内嵌图片识别 ---\n");
            sb.append("文档包含 ").append(images.size()).append(" 张图片，识别前 ").append(limit).append(" 张：\n");
            for (int i = 0; i < limit; i++) {
                String t = aiService.chatWithImage(userId, "请识别这张图片的内容", images.get(i));
                sb.append(i + 1).append(". ").append(t).append("\n");
            }
        }

        if (sb.length() == 0) {
            sb.append("未能从该文件中提取到文本或图片内容。");
        }
        return Map.of("ok", true, "filename", filename, "text", sb.toString(), "imageCount", images.size());
    }
}
