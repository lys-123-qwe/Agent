package com.example.youkeda_project.controller;

import com.example.youkeda_project.service.TempImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;

@RestController
public class ChartController {

    private static final Logger log = LoggerFactory.getLogger(ChartController.class);

    private final TempImageService tempImageService;

    public ChartController(TempImageService tempImageService) {
        this.tempImageService = tempImageService;
    }

    @GetMapping("/api/chart/{id}.png")
    public ResponseEntity<byte[]> getChart(@PathVariable String id) {
        var path = tempImageService.getPath(id);
        if (path == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(bytes);
        } catch (Exception e) {
            log.warn("读取图表失败: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
