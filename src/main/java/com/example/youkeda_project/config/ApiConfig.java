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

    @Value("${ai.model:deepseek-chat}")
    private String aiModel;

    @Value("${ai.history.max:10}")
    private int aiHistoryMax;

    @Value("${ai.tool.max.rounds:8}")
    private int aiToolMaxRounds;

    @Value("${ai.system.prompt:你是一个 helpful 的 AI 助手,回答简洁.}")
    private String aiSystemPrompt;

    public String getAiApiKey() { return aiApiKey; }
    public String getAiApiUrl() { return aiApiUrl; }
    public String getAiModel() { return aiModel; }
    public int getAiHistoryMax() { return aiHistoryMax; }
    public int getAiToolMaxRounds() { return aiToolMaxRounds; }
    public String getAiSystemPrompt() { return aiSystemPrompt; }

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
    public String getTranslationApiUrl() { return aiApiUrl; }

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

    @Value("${constellation.zodiacs:[]}")
    private String constellationZodiacsJson;

    @Value("${constellation.horoscope.api.key:}")
    private String constellationHoroscopeKey;

    @Value("${constellation.horoscope.api.url:https://web.juhe.cn/constellation/getAll}")
    private String constellationHoroscopeUrl;

    public String getConstellationQueryKey() { return constellationQueryKey; }
    public String getConstellationQueryUrl() { return constellationQueryUrl; }
    public String getConstellationHoroscopeKey() { return constellationHoroscopeKey; }
    public String getConstellationHoroscopeUrl() { return constellationHoroscopeUrl; }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getConstellationZodiacs() {
        try {
            return configMapper.convertValue(configMapper.readTree(constellationZodiacsJson), List.class);
        } catch (Exception e) { return List.of(); }
    }

    // ==================== 手机归属地 ====================
    @Value("${phone.api.key:}")
    private String phoneApiKey;

    @Value("${phone.api.url:https://apis.juhe.cn/mobile/get}")
    private String phoneApiUrl;

    @Value("${phone.operators:{}}")
    private String phoneOperatorsJson;

    public String getPhoneApiKey() { return phoneApiKey; }
    public String getPhoneApiUrl() { return phoneApiUrl; }

    @SuppressWarnings("unchecked")
    public Map<String, String> getPhoneOperators() {
        try {
            return configMapper.convertValue(configMapper.readTree(phoneOperatorsJson), LinkedHashMap.class);
        } catch (Exception e) { return Map.of(); }
    }

    // ==================== 博查搜索 ====================
    @Value("${bocha.api.key:}")
    private String bochaApiKey;

    @Value("${bocha.api.url:https://api.bochaai.com/v1/web-search}")
    private String bochaApiUrl;

    @Value("${bocha.default.count:10}")
    private int bochaDefaultCount;

    @Value("${bocha.max.count:20}")
    private int bochaMaxCount;

    public String getBochaApiKey() { return bochaApiKey; }
    public String getBochaApiUrl() { return bochaApiUrl; }
    public int getBochaDefaultCount() { return bochaDefaultCount; }
    public int getBochaMaxCount() { return bochaMaxCount; }

    // ==================== 股票行情 ====================
    @Value("${stock.sina.api.url:http://hq.sinajs.cn/list=}")
    private String stockSinaApiUrl;

    @Value("${stock.tencent.api.url:http://qt.gtimg.cn/q=}")
    private String stockTencentApiUrl;

    @Value("${stock.sina.referer.url:https://finance.sina.com.cn}")
    private String stockSinaRefererUrl;

    @Value("${stock.name.map:{}}")
    private String stockNameMapJson;

    public String getStockSinaApiUrl() { return stockSinaApiUrl; }
    public String getStockTencentApiUrl() { return stockTencentApiUrl; }
    public String getStockSinaRefererUrl() { return stockSinaRefererUrl; }

    @SuppressWarnings("unchecked")
    public Map<String, String> getStockNameMap() {
        try {
            return configMapper.convertValue(configMapper.readTree(stockNameMapJson), LinkedHashMap.class);
        } catch (Exception e) { return Map.of(); }
    }

    // ==================== iLink 协议 ====================
    @Value("${ilink.base.url:https://ilinkai.weixin.qq.com}")
    private String ilinkBaseUrl;

    @Value("${ilink.cdn.base.url:https://novac2c.cdn.weixin.qq.com/c2c}")
    private String ilinkCdnBaseUrl;

    @Value("${ilink.bot.type:3}")
    private int ilinkBotType;

    @Value("${ilink.channel.version:1.0.2}")
    private String ilinkChannelVersion;

    @Value("${ilink.auth.type:ilink_bot_token}")
    private String ilinkAuthType;

    @Value("${ilink.poll.timeout:35000}")
    private int ilinkPollTimeout;

    @Value("${ilink.request.timeout:15000}")
    private int ilinkRequestTimeout;

    @Value("${ilink.cdn.timeout:10000}")
    private int ilinkCdnTimeout;

    @Value("${ilink.poll.interval:3000}")
    private int ilinkPollInterval;

    @Value("${ilink.qrcode.poll.interval:2000}")
    private int ilinkQrcodePollInterval;

    public String getIlinkBaseUrl() { return ilinkBaseUrl; }
    public String getIlinkCdnBaseUrl() { return ilinkCdnBaseUrl; }
    public int getIlinkBotType() { return ilinkBotType; }
    public String getIlinkChannelVersion() { return ilinkChannelVersion; }
    public String getIlinkAuthType() { return ilinkAuthType; }
    public int getIlinkPollTimeout() { return ilinkPollTimeout; }
    public int getIlinkRequestTimeout() { return ilinkRequestTimeout; }
    public int getIlinkCdnTimeout() { return ilinkCdnTimeout; }
    public int getIlinkPollInterval() { return ilinkPollInterval; }
    public int getIlinkQrcodePollInterval() { return ilinkQrcodePollInterval; }

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

    // ==================== 节日 ====================
    @Value("${holidays:[]}")
    private String holidaysJson;

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getHolidays() {
        try {
            return configMapper.convertValue(configMapper.readTree(holidaysJson), List.class);
        } catch (Exception e) { return List.of(); }
    }

    // ==================== 快递物流查询 ====================
    @Value("${express.api.appcode:}")
    private String expressApiAppCode;

    @Value("${express.api.url:}")
    private String expressApiUrl;

    public String getExpressApiAppCode() { return expressApiAppCode; }
    public String getExpressApiUrl() { return expressApiUrl; }

    // 兼容旧接口
    public String getNewsApiKey() { return ""; }
    public String getNewsSdkApiKey() { return ""; }

    // ==================== 缘份居（算命/黄历/面相） ====================
    @Value("${yuanfenju.api.key:}")
    private String yuanfenjuApiKey;

    public String getYuanfenjuApiKey() { return yuanfenjuApiKey; }
}
