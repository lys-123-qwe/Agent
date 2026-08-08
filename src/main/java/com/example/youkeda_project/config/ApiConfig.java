package com.example.youkeda_project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/** 全部外部 API 的 Key/URL/模型配置（Spring @Value 注入）。 */


@Configuration
public class ApiConfig {

    private final ObjectMapper configMapper = new ObjectMapper();

    // ==================== 高德地图 ====================
    @Value("${amap.api.key:}")
    private String amapApiKey;

    @Value("${amap.api.geo.url:https://restapi.amap.com/v3/geocode/geo}")
    private String amapGeoUrl;

    public String getAmapApiKey() { return amapApiKey; }
    public String getAmapGeoUrl() { return amapGeoUrl; }

    // ==================== 和风天气 ====================
    @Value("${weather.api.key:}")
    private String weatherApiKey;

    @Value("${weather.api.url:https://devapi.qweather.com/v7/weather/now}")
    private String weatherApiUrl;

    public String getWeatherApiKey() { return weatherApiKey; }
    public String getWeatherApiUrl() { return weatherApiUrl; }

    // ==================== AI 模型(DeepSeek) ====================
    @Value("${ai.api.key:}")
    private String aiApiKey;

    @Value("${ai.api.url:https://api.deepseek.com/chat/completions}")
    private String aiApiUrl;

    @Value("${ai.tool.max.rounds:8}")
    private int aiToolMaxRounds;

    public String getAiApiKey() { return aiApiKey; }
    public String getAiApiUrl() { return aiApiUrl; }
    public int getAiToolMaxRounds() { return aiToolMaxRounds; }

    // ==================== 通义万相(图片生成) ====================
    @Value("${image.api.key:}")
    private String imageApiKey;

    @Value("${image.api.url:https://dashscope.aliyuncs.com}")
    private String imageApiUrl;

    @Value("${image.model:wan2.6-t2i}")
    private String imageModel;

    @Value("${image.edit.model:wan2.6-image}")
    private String imageEditModel;

    @Value("${image.generate.size:1280*1280}")
    private String imageGenerateSize;

    @Value("${image.edit.size:1K}")
    private String imageEditSize;

    public String getImageApiKey() { return imageApiKey; }
    public String getImageApiUrl() { return imageApiUrl; }
    public String getImageModel() { return imageModel; }
    public String getImageEditModel() { return imageEditModel; }
    public String getImageGenerateSize() { return imageGenerateSize; }
    public String getImageEditSize() { return imageEditSize; }

    /** 翻译/文案/算命等通用 LLM 调用，复用 DeepSeek API URL。 */
    public String getTranslationApiUrl() { return getVisionApiUrl(); }

    // ==================== 通义千问 VL(图片识别) ====================
    @Value("${vision.api.url:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String visionApiUrl;

    @Value("${vision.model:qwen-vl-plus}")
    private String visionModel;

    public String getVisionApiKey() { return imageApiKey; }
    public String getVisionApiUrl() { return visionApiUrl; }
    public String getVisionModel() { return visionModel; }

    // ==================== Embedding(向量化,知识库语义检索) ====================
    @Value("${embedding.api.url:https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding}")
    private String embeddingApiUrl;

    @Value("${embedding.model:text-embedding-v3}")
    private String embeddingModel;

    /** 复用 Dashscope API Key(与图片生成/VL/ASR 共用) */
    public String getEmbeddingApiKey() { return imageApiKey; }
    public String getEmbeddingApiUrl() { return embeddingApiUrl; }
    public String getEmbeddingModel() { return embeddingModel; }

    // ==================== 语音识别(阿里云 ASR) ====================
    @Value("${voice.asr.api.url:https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation}")
    private String voiceAsrApiUrl;

    @Value("${voice.asr.model:qwen3-asr-flash}")
    private String voiceAsrModel;

    @Value("${voice.sample.rate:24000}")
    private int voiceSampleRate;

    public String getVoiceApiKey() { return imageApiKey; }
    public String getVoiceAsrApiUrl() { return voiceAsrApiUrl; }
    public String getVoiceAsrModel() { return voiceAsrModel; }
    public int getVoiceSampleRate() { return voiceSampleRate; }

    // ==================== 语音合成 TTS ====================
    @Value("${tts.api.url:https://dashscope.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer}")
    private String ttsApiUrl;

    @Value("${tts.api.model:cosyvoice-v3-flash}")
    private String ttsApiModel;

    @Value("${tts.default.voice:longanyang}")
    private String ttsDefaultVoice;

    @Value("${tts.max.text.length:500}")
    private int ttsMaxTextLength;

    @Value("${tts.sample.rate:24000}")
    private int ttsSampleRate;

    @Value("${tts.mp3.bitrate:128000}")
    private int ttsMp3Bitrate;

    public String getTtsApiUrl() { return ttsApiUrl; }
    public String getTtsApiModel() { return ttsApiModel; }
    public String getTtsDefaultVoice() { return ttsDefaultVoice; }
    public int getTtsMaxTextLength() { return ttsMaxTextLength; }
    public int getTtsSampleRate() { return ttsSampleRate; }
    public int getTtsMp3Bitrate() { return ttsMp3Bitrate; }

    private JsonNode ttsConfig;

    private JsonNode getTtsConfig() {
        if (ttsConfig == null) {
            try {
                InputStream is = new ClassPathResource("tts-voices.json").getInputStream();
                ttsConfig = configMapper.readTree(is);
            } catch (Exception e) {
                return configMapper.createObjectNode();
            }
        }
        return ttsConfig;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> getTtsVoices() {
        try {
            return configMapper.convertValue(getTtsConfig().path("voices"), List.class);
        } catch (Exception e) { return List.of(); }
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getTtsVoiceAliases() {
        try {
            return configMapper.convertValue(getTtsConfig().path("aliases"), LinkedHashMap.class);
        } catch (Exception e) { return Map.of(); }
    }

    // ==================== 汇率计算 ====================
    @Value("${exchange.api.key:}")
    private String exchangeApiKey;

    @Value("${exchange.api.url:https://v6.exchangerate-api.com/v6}")
    private String exchangeApiUrl;

    public String getExchangeApiKey() { return exchangeApiKey; }
    public String getExchangeApiUrl() { return exchangeApiUrl; }

    // ==================== 星座 ====================
    @Value("${constellation.query.api.key:}")
    private String constellationQueryKey;

    @Value("${constellation.query.api.url:https://apis.juhe.cn/fapig/constellation/query}")
    private String constellationQueryUrl;

    @Value("${constellation.horoscope.api.key:}")
    private String constellationHoroscopeKey;

    @Value("${constellation.horoscope.api.url:https://web.juhe.cn/constellation/getAll}")
    private String constellationHoroscopeUrl;

    public String getConstellationQueryKey() { return constellationQueryKey; }
    public String getConstellationQueryUrl() { return constellationQueryUrl; }
    public String getConstellationHoroscopeKey() { return constellationHoroscopeKey; }
    public String getConstellationHoroscopeUrl() { return constellationHoroscopeUrl; }


    // ==================== 手机归属地 ====================
    @Value("${phone.api.key:}")
    private String phoneApiKey;

    @Value("${phone.api.url:https://apis.juhe.cn/mobile/get}")
    private String phoneApiUrl;

    public String getPhoneApiKey() { return phoneApiKey; }
    public String getPhoneApiUrl() { return phoneApiUrl; }


    // ==================== 博查搜索 ====================
    @Value("${bocha.api.key:}")
    private String bochaApiKey;

    @Value("${bocha.api.url:https://api.bochaai.com/v1/web-search}")
    private String bochaApiUrl;

    public String getBochaApiKey() { return bochaApiKey; }
    public String getBochaApiUrl() { return bochaApiUrl; }

    // ==================== iLink 协议 ====================
    @Value("${ilink.base.url:https://ilinkai.weixin.qq.com}")
    private String ilinkBaseUrl;

    public String getIlinkBaseUrl() { return ilinkBaseUrl; }

    // ==================== 文件解析 ====================
    @Value("${file.max.size:20971520}")
    private long fileMaxSize;

    @Value("${file.max.text.length:8000}")
    private int fileMaxTextLength;

    @Value("${file.plaintext.extensions:[]}")
    private String filePlaintextExtensionsJson;

    public long getFileMaxSize() { return fileMaxSize; }
    public int getFileMaxTextLength() { return fileMaxTextLength; }

    @SuppressWarnings("unchecked")
    public List<String> getFilePlaintextExtensions() {
        try {
            return configMapper.convertValue(configMapper.readTree(filePlaintextExtensionsJson), List.class);
        } catch (Exception e) { return List.of(); }
    }

    // ==================== 快递物流查询 ====================
    @Value("${express.api.appcode:}")
    private String expressApiAppCode;

    @Value("${express.api.url:}")
    private String expressApiUrl;

    public String getExpressApiAppCode() { return expressApiAppCode; }
    public String getExpressApiUrl() { return expressApiUrl; }


    // ==================== 缘份居（算命/黄历/面相） ====================
    @Value("${yuanfenju.api.key:}")
    private String yuanfenjuApiKey;

    public String getYuanfenjuApiKey() { return yuanfenjuApiKey; }
}
