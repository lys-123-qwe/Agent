package com.example.youkeda_project.controller;

import com.example.youkeda_project.ai.AIService;
import com.example.youkeda_project.service.wechat.FileService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    /** 上传文件并提取文本（Tika 多格式）。 */
    @PostMapping("/file")
    public Map<String, Object> file(@RequestParam(defaultValue = "web") String userId,
                                    @RequestParam("file") MultipartFile file) throws Exception {
        String text = fileService.extractText(file.getBytes(), file.getOriginalFilename());
        return Map.of("ok", true, "filename", file.getOriginalFilename(), "text", text);
    }
}
