package com.example.youkeda_project.service;

import com.example.youkeda_project.config.ApiConfig;
import io.github.kasukusakura.silkcodec.SilkCoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * 语音合成（TTS）服务，基于阿里云 dashscope CosyVoice-v3-flash 非实时 HTTP API。
 * <p>
 * 支持多轮交互状态管理，流程：
 * 1. 用户首次表达朗读意图 → 获取文本内容（如未指定则追问）
 * 2. 确定文本后 → 请用户选择音色
 * 3. 用户选择音色后 → 调用 TTS API 合成 → 返回音频字节
 * <p>
 * API 说明：
 * - 端点：POST /api/v1/services/audio/tts/SpeechSynthesizer（非实时 HTTP）
 * - 模型：cosyvoice-v3-flash
 * - 响应：返回音频文件 URL（有效期 24h），需再下载
 */
@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    enum TtsState { IDLE, WAITING_TEXT, WAITING_VOICE }

    static class TtsSession {
        TtsState state = TtsState.IDLE;
        String text = "";
    }

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ApiConfig apiConfig;
    private final Map<String, TtsSession> sessions = new ConcurrentHashMap<>();

    public TtsService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public TtsSession getOrCreateSession(String userId) {
        return sessions.computeIfAbsent(userId, k -> new TtsSession());
    }

    public void clearSession(String userId) {
        sessions.remove(userId);
    }

    /**
     * 调用 dashscope CosyVoice-v3-flash 非实时 HTTP API 合成语音，
     * 返回 SILK 格式（用于尝试发送语音消息）。
     */
    public TtsResult synthesize(String text, String voice) {
        byte[] wavBytes = callTtsApi(text, voice, "wav");
        return convertWavToSilk(wavBytes);
    }

    /**
     * 调用 TTS 并返回 MP3 格式（用于发送文件消息）。
     */
    public TtsResult synthesizeMp3(String text, String voice) {
        byte[] mp3Bytes = callTtsApi(text, voice, "mp3");
        // MP3 按 128kbps 估算时长
        int bitrate = apiConfig.getTtsMp3Bitrate();
        int durationSeconds = Math.max(1, mp3Bytes.length * 8 / bitrate);
        log.info("TTS MP3 下载完成: {} bytes, 估算时长: {}s", mp3Bytes.length, durationSeconds);
        return new TtsResult(mp3Bytes, durationSeconds);
    }

    /**
     * 通用 TTS API 调用：请求指定格式并下载音频字节。
     */
    private byte[] callTtsApi(String text, String voice, String format) {
        String apiKey = apiConfig.getVoiceApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("未配置阿里云 API Key，无法使用语音合成");
        }

        // 截断过长的文本
        int maxLen = apiConfig.getTtsMaxTextLength();
        if (text.length() > maxLen) {
            text = text.substring(0, maxLen);
            log.warn("TTS 文本超长，已截断至 {} 字符", maxLen);
        }

        try {
            Map<String, Object> requestBody = Map.of(
                "model", apiConfig.getTtsApiModel(),
                "input", Map.of(
                    "text", text,
                    "voice", voice,
                    "format", format,
                    "sample_rate", apiConfig.getTtsSampleRate()
                )
            );

            String bodyJson = objectMapper.writeValueAsString(requestBody);
            log.info("CosyVoice TTS 请求: model={}, voice={}, format={}, textLen={}",
                apiConfig.getTtsApiModel(), voice, format, text.length());

            byte[] bodyBytes = bodyJson.getBytes(StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiConfig.getTtsApiUrl()))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                .timeout(Duration.ofSeconds(30))
                .build();
            HttpResponse<byte[]> httpResp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            int statusCode = httpResp.statusCode();
            log.info("TTS API HTTP 状态码: {}", statusCode);
            byte[] responseBytes = httpResp.body();
            if (statusCode != 200) {
                String errBody = new String(responseBytes, StandardCharsets.UTF_8);
                throw new RuntimeException("TTS API HTTP " + statusCode + ": " + errBody);
            }

            String responseJson = new String(responseBytes, StandardCharsets.UTF_8);
            log.info("CosyVoice TTS 响应: {}", responseJson);

            JsonNode root = objectMapper.readTree(responseJson);

            JsonNode code = root.path("code");
            if (!code.isMissingNode()) {
                String message = root.path("message").asText("未知错误");
                throw new RuntimeException("TTS API 错误: " + message + " (code=" + code.asText() + ")");
            }

            String audioUrl = root.path("output").path("audio").path("url").asText();
            if (audioUrl.isBlank()) {
                throw new RuntimeException("TTS 响应中未包含音频 URL");
            }

            log.info("TTS 音频 URL: {}", audioUrl);
            HttpRequest audioReq = HttpRequest.newBuilder()
                .uri(URI.create(audioUrl))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
            HttpResponse<byte[]> audioResp = httpClient.send(audioReq, HttpResponse.BodyHandlers.ofByteArray());
            if (audioResp.statusCode() != 200) {
                throw new RuntimeException("下载 TTS 音频失败: HTTP " + audioResp.statusCode());
            }
            byte[] audioBytes = audioResp.body();

            if (audioBytes == null || audioBytes.length == 0) {
                throw new RuntimeException("下载 TTS 音频失败: 返回数据为空");
            }

            log.info("TTS {} 下载完成: {} bytes", format, audioBytes.length);
            return audioBytes;

        } catch (Exception e) {
            log.error("TTS 调用失败", e);
            throw new RuntimeException("语音合成失败: " + e.getMessage(), e);
        }
    }

    /**
     * WAV → PCM → SILK（含 WeChat 0x02 前缀）。
     */
    private TtsResult convertWavToSilk(byte[] wavBytes) {
        try {
            ByteArrayInputStream wavIn = new ByteArrayInputStream(wavBytes);
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(wavIn);
            AudioFormat format = audioInputStream.getFormat();
            int sampleRate = (int) format.getSampleRate();
            int channels = format.getChannels();
            int bitsPerSample = format.getSampleSizeInBits();

            ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = audioInputStream.read(buf)) != -1) {
                pcmOut.write(buf, 0, len);
            }
            audioInputStream.close();
            byte[] pcmData = pcmOut.toByteArray();

            if (pcmData.length == 0) {
                throw new RuntimeException("WAV 中未提取到 PCM 数据");
            }

            int bytesPerSec = sampleRate * channels * bitsPerSample / 8;
            int durationSeconds = Math.max(1, pcmData.length / bytesPerSec);
            log.info("PCM 数据: {} bytes, sampleRate={}, channels={}, bitsPerSample={}, 估算时长: {}s",
                pcmData.length, sampleRate, channels, bitsPerSample, durationSeconds);

            ByteArrayInputStream pcmIn = new ByteArrayInputStream(pcmData);
            ByteArrayOutputStream silkOut = new ByteArrayOutputStream();
            SilkCoder.encode(pcmIn, silkOut, sampleRate);
            byte[] silkBytes = silkOut.toByteArray();

            byte[] silkWithPrefix = new byte[silkBytes.length + 1];
            silkWithPrefix[0] = 0x02;
            System.arraycopy(silkBytes, 0, silkWithPrefix, 1, silkBytes.length);

            log.info("PCM→SILK 编码完成: {} SILK bytes (前缀后 {} bytes, 时长 {}s)",
                silkBytes.length, silkWithPrefix.length, durationSeconds);

            return new TtsResult(silkWithPrefix, durationSeconds);

        } catch (Exception e) {
            log.error("WAV→SILK 转换失败", e);
            throw new RuntimeException("WAV→SILK 转换失败: " + e.getMessage(), e);
        }
    }

    /**
     * TTS 合成结果：音频字节 + 语音时长（秒）。
     */
    public record TtsResult(byte[] audioBytes, int durationSeconds) {}

    /**
     * 音色信息。
     */
    public record VoiceInfo(String voiceId, String name, String description) {}

    /**
     * 获取所有可用音色。
     */
    public List<VoiceInfo> getVoices() {
        List<Map<String, String>> raw = apiConfig.getTtsVoices();
        return raw.stream()
            .map(m -> new VoiceInfo(
                m.getOrDefault("voiceId", ""),
                m.getOrDefault("name", ""),
                m.getOrDefault("description", "")))
            .toList();
    }

    /**
     * 获取音色选择提示文字。
     */
    public String getVoicePrompt() {
        List<Map<String, String>> voices = apiConfig.getTtsVoices();
        StringBuilder sb = new StringBuilder("请选择音色（回复数字或音色名）：\n");
        for (int i = 0; i < voices.size(); i++) {
            Map<String, String> v = voices.get(i);
            sb.append(i + 1).append(". ").append(v.get("name")).append(" - ").append(v.get("description")).append("\n");
        }
        sb.append("\n回复 \"取消\" 取消操作，或发送其他内容继续对话");
        return sb.toString();
    }

    /**
     * 解析用户输入的音色选择。
     * 支持：数字 1-5、音色名（龙小淳）、voice ID（longxiaochun）。
     * 返回匹配的 voice ID，未匹配返回 null。
     */
    public String resolveVoice(String userInput) {
        if (userInput == null || userInput.isBlank()) return null;

        String trimmed = userInput.trim();
        List<Map<String, String>> voices = apiConfig.getTtsVoices();

        // 数字匹配
        try {
            String numStr = trimmed.replaceAll("[号.。、\\s]", "").trim();
            if (!numStr.isEmpty()) {
                int idx = Integer.parseInt(numStr) - 1;
                if (idx >= 0 && idx < voices.size()) {
                    return voices.get(idx).get("voiceId");
                }
            }
        } catch (NumberFormatException ignored) {}

        // 直接匹配 voice ID
        for (Map<String, String> v : voices) {
            if (v.get("voiceId").equalsIgnoreCase(trimmed)) return v.get("voiceId");
        }

        // 匹配中文名
        for (Map<String, String> v : voices) {
            if (v.get("name").equals(trimmed)) return v.get("voiceId");
        }

        // 部分匹配
        for (Map<String, String> v : voices) {
            if (trimmed.contains(v.get("name")) || v.get("name").contains(trimmed)) return v.get("voiceId");
        }

        // 别名映射
        String aliasMatch = apiConfig.getTtsVoiceAliases().get(trimmed);
        if (aliasMatch != null) return aliasMatch;

        return null;
    }

    public String getDefaultVoice() {
        return apiConfig.getTtsDefaultVoice();
    }
}
