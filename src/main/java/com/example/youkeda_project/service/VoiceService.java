package com.example.youkeda_project.service;

import com.example.youkeda_project.config.ApiConfig;
import io.github.kasukusakura.silkcodec.SilkCoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 语音识别服务。
 * <p>
 * 从微信 CDN 下载语音文件，检测音频格式，调用阿里云 dashscope qwen3-asr-flash
 * 进行语音转文字（ASR），返回识别后的文本供 AI 处理。
 * <p>
 * 策略：微信 CDN 的下载 URL 无法被 dashscope 直接访问，Paraformer 又只接受
 * http/https URL（不支持 oss:// 或 base64），因此改用 qwen3-asr-flash 模型
 * 通过 multimodal-generation 端点同步识别，音频以 base64 data URI 形式传入。
 * <p>
 * SILK 格式处理：若直接发送 SILK 到 API 返回空结果，则使用 silk-codec 库
 * 将 SILK 解码为 PCM，再封装为 WAV 格式后重试。
 */
@Service
public class VoiceService {

    private static final Logger log = LoggerFactory.getLogger(VoiceService.class);

    private static final String ASR_MODEL = "qwen3-asr-flash";
    private static final String ASR_API_URL =
        "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

    private static final int WAV_SAMPLE_RATE = 24000;
    private static final int WAV_BITS_PER_SAMPLE = 16;
    private static final int WAV_CHANNELS = 1;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ApiConfig apiConfig;

    public VoiceService(ApiConfig apiConfig, ObjectMapper objectMapper) {
        this.apiConfig = apiConfig;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    /**
     * 语音识别入口：检测格式后发送到 ASR。
     * 如果检测为 SILK 且直接发送失败（空结果），则自动转换为 WAV 后重试。
     */
    public String transcribe(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length == 0) {
            return "错误: 语音数据为空";
        }

        String format = detectAudioFormat(audioBytes);
        log.info("语音格式: {}, 大小: {} bytes", format, audioBytes.length);

        try {
            String result = transcribeSync(audioBytes, format);
            // 如果 SILK 格式返回空结果，尝试转换为 WAV 后重试
            if ("silk".equals(format) && (result.startsWith("错误:") || result.contains("未能从响应中提取"))) {
                log.info("SILK 格式直接发送未得到有效结果，尝试转换为 WAV...");
                byte[] wavBytes = silkToWav(audioBytes);
                if (wavBytes != null) {
                    log.info("SILK→WAV 转换成功: {} bytes", wavBytes.length);
                    return transcribeSync(wavBytes, "wav");
                } else {
                    log.warn("SILK→WAV 转换失败，返回原始结果");
                }
            }
            return result;
        } catch (Exception e) {
            log.error("语音识别失败", e);
            return "语音识别失败: " + e.getMessage();
        }
    }

    /**
     * 通过 base64 audio data URI 调用 qwen3-asr-flash 同步识别。
     * <p>
     * 端点: POST /api/v1/services/aigc/multimodal-generation/generation
     * 模型: qwen3-asr-flash（专用 ASR 模型，支持 wav/mp3/amr/ogg/flac/m4a）
     */
    private String transcribeSync(byte[] audioBytes, String format) throws Exception {
        String apiKey = apiConfig.getVoiceApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "错误: 未配置阿里云 API Key，无法使用语音识别";
        }

        String mimeType = getMimeType(format);
        String base64 = Base64.getEncoder().encodeToString(audioBytes);
        String dataUri = "data:" + mimeType + ";base64," + base64;

        // 构造请求体（qwen3-asr-flash 同步 ASR 格式，含 asr_options）
        Map<String, Object> requestBody = Map.of(
            "model", ASR_MODEL,
            "input", Map.of(
                "messages", List.of(Map.of(
                    "role", "user",
                    "content", List.of(Map.of("audio", dataUri))
                ))
            ),
            "parameters", Map.of(
                "asr_options", Map.of(
                    "enable_itn", true
                )
            )
        );

        String bodyJson = objectMapper.writeValueAsString(requestBody);
        log.info("qwen3-asr-flash 请求: {} ({} bytes)", format, audioBytes.length);

        byte[] bodyBytes = bodyJson.getBytes(StandardCharsets.UTF_8);
        byte[] responseBytes = restClient.post()
                .uri(ASR_API_URL)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + apiKey)
                .body(bodyBytes)
                .retrieve()
                .body(byte[].class);

        String responseJson = new String(responseBytes, StandardCharsets.UTF_8);
        log.info("qwen3-asr-flash 响应: {}", responseJson);

        JsonNode root = objectMapper.readTree(responseJson);

        // 检查是否有错误
        JsonNode code = root.path("code");
        if (!code.isMissingNode()) {
            String message = root.path("message").asText("未知错误");
            return "错误: " + message + " (code=" + code.asText() + ")";
        }

        // 解析转录结果
        JsonNode choices = root.path("output").path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode message = choices.get(0).path("message");
            JsonNode content = message.path("content");

            // content 可能是数组 [{"text":"xxx"}] 或字符串 "xxx"
            if (content.isArray() && !content.isEmpty()) {
                // 数组格式：取第一个元素的 text
                JsonNode first = content.get(0);
                if (first.isObject() && first.has("text")) {
                    String text = first.path("text").asText("");
                    if (!text.isBlank()) {
                        return text;
                    }
                }
                // 或者数组元素直接是文本
                if (first.isTextual()) {
                    String text = first.asText("");
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            } else if (content.isTextual()) {
                // 字符串格式（某些 API 变体）
                String textContent = content.asText("");
                if (!textContent.isBlank()) {
                    return textContent;
                }
            }
        }

        // 检查 async 任务相关的响应
        JsonNode output = root.path("output");
        if (output.has("task_status")) {
            return "错误: 异步任务状态 " + output.path("task_status").asText() + "，请检查任务 ID: "
                + output.path("task_id").asText();
        }

        return "错误: 未能从响应中提取识别文本，响应: " + responseJson;
    }

    /**
     * 将 SILK 音频字节解码为 PCM，再封装为 WAV 格式。
     * <p>
     * 处理步骤：
     * 1. 去除 WeChat 添加的 ASCII STX 前缀字节（0x02）
     * 2. 使用 silk-codec JNI 库解码为 PCM s16le
     * 3. 创建 WAV 头部（16kHz 或 24kHz，单声道，16bit）
     * 4. 拼接 WAV 头部 + PCM 数据
     */
    byte[] silkToWav(byte[] silkBytes) {
        try {
            byte[] cleanSilk = silkBytes;

            // WeChat 在 SILK 头部前添加了 0x02 (STX) 前缀
            if (silkBytes.length > 0 && silkBytes[0] == 0x02) {
                cleanSilk = new byte[silkBytes.length - 1];
                System.arraycopy(silkBytes, 1, cleanSilk, 0, cleanSilk.length);
                log.info("已去除 WeChat STX 前缀字节");
            }

            // 解码 SILK → PCM s16le（strict=false 以兼容 WeChat 的 SILK 变体）
            ByteArrayInputStream silkIn = new ByteArrayInputStream(cleanSilk);
            ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
            log.info("开始 SILK→PCM 解码: {} SILK bytes (去除前缀后)", cleanSilk.length);
            SilkCoder.decode(silkIn, pcmOut, false, WAV_SAMPLE_RATE, 0);
            byte[] pcmData = pcmOut.toByteArray();

            log.info("SILK→PCM 解码完成: {} PCM bytes", pcmData.length);

            if (pcmData.length == 0) {
                log.warn("PCM 数据为空");
                return null;
            }

            // 创建 WAV 头部并拼接
            byte[] wavHeader = createWavHeader(pcmData.length, WAV_SAMPLE_RATE,
                WAV_BITS_PER_SAMPLE, WAV_CHANNELS);
            byte[] wavBytes = new byte[wavHeader.length + pcmData.length];
            System.arraycopy(wavHeader, 0, wavBytes, 0, wavHeader.length);
            System.arraycopy(pcmData, 0, wavBytes, wavHeader.length, pcmData.length);

            return wavBytes;
        } catch (Exception e) {
            log.error("SILK→WAV 转换失败", e);
            return null;
        }
    }

    /**
     * 生成 PCM s16le 的 WAV 文件头。
     * <p>
     * WAV/RIFF 结构: RIFF header + fmt chunk + data chunk
     */
    static byte[] createWavHeader(int dataSize, int sampleRate, int bitsPerSample, int channels) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataChunkSize = dataSize;
        int fileSize = 36 + dataChunkSize;

        ByteBuffer buf = ByteBuffer.allocate(44);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        // RIFF header
        buf.put((byte) 'R');
        buf.put((byte) 'I');
        buf.put((byte) 'F');
        buf.put((byte) 'F');
        buf.putInt(fileSize);
        buf.put((byte) 'W');
        buf.put((byte) 'A');
        buf.put((byte) 'V');
        buf.put((byte) 'E');

        // fmt chunk
        buf.put((byte) 'f');
        buf.put((byte) 'm');
        buf.put((byte) 't');
        buf.put((byte) ' ');
        buf.putInt(16);           // chunk size
        buf.putShort((short) 1);  // PCM = 1
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(byteRate);
        buf.putShort((short) blockAlign);
        buf.putShort((short) bitsPerSample);

        // data chunk
        buf.put((byte) 'd');
        buf.put((byte) 'a');
        buf.put((byte) 't');
        buf.put((byte) 'a');
        buf.putInt(dataChunkSize);

        return buf.array();
    }

    /**
     * 将音频格式名称映射为 MIME 类型，用于构造 data URI。
     */
    private String getMimeType(String format) {
        return switch (format) {
            case "wav" -> "audio/wav";
            case "mp3" -> "audio/mpeg";
            case "amr" -> "audio/amr";
            case "ogg" -> "audio/ogg";
            case "flac" -> "audio/flac";
            case "m4a" -> "audio/mp4";
            case "silk" -> "audio/silk";
            default -> "audio/wav";
        };
    }

    /**
     * 在字节数组中搜索魔数字符串，返回首次匹配的偏移量，未匹配返回 -1。
     */
    private static int indexOfMagic(byte[] data, String magic) {
        byte[] pattern = magic.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int end = Math.min(data.length - pattern.length, 32);
        for (int i = 0; i <= end; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    /**
     * 通过文件魔数（Magic Number）检测音频格式。
     */
    static String detectAudioFormat(byte[] data) {
        if (data == null || data.length < 4) return "wav";

        if (data[0] == 0x52 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x46) {
            return "wav";
        }
        if (data.length > 3 && data[0] == 0x49 && data[1] == 0x44 && data[2] == 0x33) {
            return "mp3";
        }
        if (data.length > 2 && data[0] == (byte) 0xFF && (data[1] & 0xF0) == 0xF0) {
            return "mp3";
        }
        if (data.length > 5 && data[0] == 0x23 && data[1] == 0x21 &&
            data[2] == 0x41 && data[3] == 0x4D && data[4] == 0x52) {
            return "amr";
        }
        if (data[0] == 0x4F && data[1] == 0x67 && data[2] == 0x67 && data[3] == 0x53) {
            return "ogg";
        }
        if (data[0] == 0x66 && data[1] == 0x4C && data[2] == 0x61 && data[3] == 0x43) {
            return "flac";
        }
        if (data.length > 8 && data[4] == 0x66 && data[5] == 0x74 && data[6] == 0x79 && data[7] == 0x70) {
            return "m4a";
        }
        // SILK 检测：SILK 在 WeChat 中可能有 1-3 字节前缀，或者直接以 SILK/SILK_U 开头
        // 常见模式: \x02#'SILK_U, #!SILK_V3, SILK_U
        int silkOffset = indexOfMagic(data, "SILK");
        if (silkOffset >= 0 && silkOffset < 16) {
            log.warn("检测到 SILK 格式（偏移 {}），qwen3-asr-flash 可能不支持此格式", silkOffset);
            return "silk";
        }

        return "wav";
    }
}
