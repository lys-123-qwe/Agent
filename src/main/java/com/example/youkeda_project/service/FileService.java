package com.example.youkeda_project.service;

import com.example.youkeda_project.config.ApiConfig;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件文本提取服务.
 * <p>
 * 使用 Apache Tika 自动检测文件格式并提取纯文本内容.
 * 支持 txt,docx,xlsx,pptx,pdf,html,csv 等常见格式.
 * 提取结果截断到 MAX_TEXT_LENGTH 字符,避免 AI token 超限.
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /** 兼容旧代码的静态常量 */
    public static final long MAX_FILE_SIZE = 20971520;

    private final Tika tika = new Tika();
    private final ApiConfig apiConfig;

    public long getMaxFileSize() {
        return apiConfig.getFileMaxSize();
    }

    public FileService(ApiConfig apiConfig) {
        this.apiConfig = apiConfig;
    }


    /**
     * 从文件字节数组中提取文本内容.
     * <p>
     * Tika 通过文件头 magic bytes 自动检测格式,不依赖扩展名.
     * 纯文本类文件(txt/csv/json/xml/md)直接用 UTF-8 读取,跳过 Tika 解析开销.
     *
     * @param fileBytes 解密后的文件字节
     * @param fileName  文件名(含扩展名,用于日志和快速路径判断)
     * @return 提取的文本内容,超长时截断并附加提示
     */
    public String extractText(byte[] fileBytes, String fileName) {
        if (fileBytes == null || fileBytes.length == 0) {
            return "(文件内容为空)";
        }

        String ext = getExtension(fileName).toLowerCase();
        log.info("文件解析: {}, {} bytes, 扩展名: {}", fileName, fileBytes.length, ext);

        // 纯文本类文件直接读取,避免 Tika 开销
        if (isPlainText(ext)) {
            return readAsPlainText(fileBytes, ext);
        }

        // 其他格式走 Tika 解析
        try {
            String text = tika.parseToString(new ByteArrayInputStream(fileBytes));
            if (text == null || text.isBlank()) {
                return "(未能从文件中提取到文本内容)";
            }
            return truncate(text);
        } catch (Exception e) {
            log.error("Tika 解析失败: {}", fileName, e);
            return "文件解析失败: " + e.getMessage();
        }
    }

    /**
     * 纯文本文件直接读取.
     */
    private String readAsPlainText(byte[] fileBytes, String ext) {
        try {
            // JSON/XML 尝试 UTF-8,CSV/TXT 也用 UTF-8
            String text = new String(fileBytes, StandardCharsets.UTF_8);
            return truncate(text);
        } catch (Exception e) {
            return "文本读取失败: " + e.getMessage();
        }
    }

    /**
     * 判断是否为纯文本类扩展名(可直接读取,无需 Tika).
     */
    private boolean isPlainText(String ext) {
        List<String> extensions = apiConfig.getFilePlaintextExtensions();
        return extensions.contains(ext);
    }

    /**
     * 截断过长文本.
     */
    private String truncate(String text) {
        int maxLen = apiConfig.getFileMaxTextLength();
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "\n\n[...内容过长,已截断至 " + maxLen + " 字符]";
    }

    /**
     * 从文件名中提取扩展名(不含点).
     */
    private String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "";
    }
}
