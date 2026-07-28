package com.example.youkeda_project.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class TempImageService {

    private static final Logger log = LoggerFactory.getLogger(TempImageService.class);

    private final ConcurrentHashMap<String, Path> images = new ConcurrentHashMap<>();
    private Path tempDir;

    @PostConstruct
    public void init() throws IOException {
        tempDir = Files.createTempDirectory("claw_charts_");
        log.info("临时图片目录: {}", tempDir);
        Executors.newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(this::cleanup, 1, 1, TimeUnit.MINUTES);
    }

    public String save(byte[] data) {
        String id = UUID.randomUUID().toString().replace("-", "");
        try {
            Path file = tempDir.resolve(id + ".png");
            Files.write(file, data);
            images.put(id, file);
            return id;
        } catch (IOException e) {
            log.warn("保存临时图片失败", e);
            return null;
        }
    }

    public Path getPath(String id) {
        return images.get(id);
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        images.values().removeIf(p -> {
            try {
                boolean expired = now - Files.getLastModifiedTime(p).toMillis() > 120_000;
                if (expired) Files.deleteIfExists(p);
                return expired;
            } catch (IOException e) {
                return true;
            }
        });
    }

    @PreDestroy
    public void destroy() throws IOException {
        if (tempDir != null) {
            try (var files = Files.list(tempDir)) {
                files.forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
            Files.deleteIfExists(tempDir);
        }
    }
}
