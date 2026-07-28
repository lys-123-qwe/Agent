package com.example.youkeda_project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiConfig {

    @Value("${amap.api.key:}")
    private String amapApiKey;

    @Value("${amap.api.url:https://restapi.amap.com/v3/geocode/geo}")
    private String amapApiUrl;

    @Value("${weather.api.key:}")
    private String weatherApiKey;

    @Value("${weather.api.url:https://devapi.qweather.com/v7/weather/now}")
    private String weatherApiUrl;

    @Value("${ai.api.key:}")
    private String aiApiKey;

    @Value("${ai.api.url:https://api.deepseek.com/chat/completions}")
    private String aiApiUrl;

    @Value("${ai.model:deepseek-chat}")
    private String aiModel;

    @Value("${ai.max.history:10}")
    private int aiMaxHistory;

    @Value("${ai.max.tool.rounds:8}")
    private int aiMaxToolRounds;

    @Value("${image.api.key:}")
    private String imageApiKey;

    @Value("${image.api.url:https://dashscope.aliyuncs.com}")
    private String imageApiUrl;

    @Value("${image.model:wan2.6-t2i}")
    private String imageModel;

    @Value("${image.default.size:1280*1280}")
    private String imageDefaultSize;

    @Value("${vision.api.url:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String visionApiUrl;

    @Value("${vision.model:qwen-vl-plus}")
    private String visionModel;

    @Value("${voice.api.url:https://dashscope.aliyuncs.com/api/v1/services/audio/asr/transcriptions}")
    private String voiceApiUrl;

    @Value("${voice.api.model:paraformer-v2}")
    private String voiceApiModel;

    @Value("${voice.asr.api.url:https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation}")
    private String voiceAsrApiUrl;

    @Value("${voice.asr.model:qwen3-asr-flash}")
    private String voiceAsrModel;

    @Value("${tts.api.url:https://dashscope.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer}")
    private String ttsApiUrl;

    @Value("${tts.api.model:cosyvoice-v3-flash}")
    private String ttsApiModel;

    @Value("${tts.default.voice:longanyang}")
    private String ttsDefaultVoice;

    @Value("${translation.api.url:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String translationApiUrl;

    @Value("${bocha.api.key:}")
    private String bochaApiKey;

    @Value("${news.api.key:}")
    private String newsApiKey;

    @Value("${news.sdk.api-key:}")
    private String newsSdkApiKey;

    @Value("${news.tian.api.url:http://api.tianapi.com/generalnews/index}")
    private String newsTianApiUrl;

    @Value("${news.bocha.search.url:https://api.bocha.cn/v1/web-search}")
    private String newsBochaSearchUrl;

    @Value("${news.bocha.hot.url:https://api.bochaai.com/v1/web-search}")
    private String newsBochaHotUrl;

    @Value("${exchange.api.key:}")
    private String exchangeApiKey;

    @Value("${exchange.api.url:https://v6.exchangerate-api.com/v6}")
    private String exchangeApiUrl;

    @Value("${constellation.query.api.key:}")
    private String constellationQueryKey;

    @Value("${constellation.query.api.url:http://web.juhe.cn:8080/constellation/getAll}")
    private String constellationQueryUrl;

    @Value("${constellation.horoscope.api.key:}")
    private String constellationHoroscopeKey;

    @Value("${constellation.horoscope.api.url:https://web.juhe.cn/constellation/getAll}")
    private String constellationHoroscopeUrl;

    @Value("${phone.api.key:}")
    private String phoneApiKey;

    @Value("${phone.api.url:http://op.juhe.cn/phone/get}")
    private String phoneApiUrl;

    @Value("${qq.music.search.url:https://c.y.qq.com/soso/fcgi-bin/client_search_cp}")
    private String qqMusicSearchUrl;

    @Value("${qq.music.lyric.url:https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg}")
    private String qqMusicLyricUrl;

    @Value("${qq.music.referer:https://y.qq.com}")
    private String qqMusicReferer;

    @Value("${qq.music.play.url:https://i.y.qq.com/v8/playsong.html?songmid=}")
    private String qqMusicPlayUrl;

    @Value("${netease.music.search.url:https://music.163.com/api/search/get/web}")
    private String neteaseMusicSearchUrl;

    @Value("${netease.music.play.url:https://music.163.com/song?id=}")
    private String neteaseMusicPlayUrl;

    @Value("${music.max.search.results:5}")
    private int musicMaxSearchResults;

    @Value("${sina.stock.api.url:http://hq.sinajs.cn/list=}")
    private String sinaStockApiUrl;

    @Value("${tencent.stock.api.url:http://qt.gtimg.cn/q=}")
    private String tencentStockApiUrl;

    @Value("${sina.referer.url:https://finance.sina.com.cn}")
    private String sinaRefererUrl;

    @Value("${ilink.base.url:https://ilinkai.weixin.qq.com}")
    private String ilinkBaseUrl;

    @Value("${ilink.cdn.base.url:https://novac2c.cdn.weixin.qq.com/c2c}")
    private String ilinkCdnBaseUrl;

    @Value("${ilink.poll.timeout.ms:35000}")
    private int ilinkPollTimeoutMs;

    @Value("${ilink.poll.interval.ms:3000}")
    private int ilinkPollIntervalMs;

    @Value("${ilink.bot.type:3}")
    private int ilinkBotType;

    @Value("${ilink.channel.version:1.0.2}")
    private String ilinkChannelVersion;

    @Value("${yuanfenju.api.key:}")
    private String yuanfenjuApiKey;

    @Value("${file.extract.max.length:8000}")
    private int fileExtractMaxLength;

    @Value("${file.max.size:20971520}")
    private int fileMaxSize;

    public String getAmapApiKey() { return amapApiKey; }
    public String getAmapGeoUrl() { return amapApiUrl; }

    public String getWeatherApiKey() { return weatherApiKey; }
    public String getWeatherApiUrl() { return weatherApiUrl; }

    public String getAiApiKey() { return aiApiKey; }
    public String getAiApiUrl() { return aiApiUrl; }
    public String getAiModel() { return aiModel; }
    public int getAiMaxHistory() { return aiMaxHistory; }
    public int getAiMaxToolRounds() { return aiMaxToolRounds; }

    public String getDeepSeekApiKey() { return aiApiKey; }
    public String getDeepSeekApiUrl() { return aiApiUrl; }

    public String getImageApiKey() { return imageApiKey; }
    public String getImageApiUrl() { return imageApiUrl; }
    public String getImageModel() { return imageModel; }
    public String getImageDefaultSize() { return imageDefaultSize; }

    public String getVisionApiKey() { return imageApiKey; }
    public String getVisionApiUrl() { return visionApiUrl; }
    public String getVisionModel() { return visionModel; }

    public String getVoiceApiKey() { return imageApiKey; }
    public String getVoiceApiUrl() { return voiceApiUrl; }
    public String getVoiceApiModel() { return voiceApiModel; }

    public String getVoiceAsrApiUrl() { return voiceAsrApiUrl; }
    public String getVoiceAsrModel() { return voiceAsrModel; }

    public String getTtsApiUrl() { return ttsApiUrl; }
    public String getTtsApiModel() { return ttsApiModel; }
    public String getTtsDefaultVoice() { return ttsDefaultVoice; }

    public String getTranslationApiUrl() { return translationApiUrl; }

    public String getBochaApiKey() { return bochaApiKey; }
    public String getNewsApiKey() { return newsApiKey; }
    public String getNewsSdkApiKey() { return newsSdkApiKey; }
    public String getNewsTianApiUrl() { return newsTianApiUrl; }
    public String getNewsBochaSearchUrl() { return newsBochaSearchUrl; }
    public String getNewsBochaHotUrl() { return newsBochaHotUrl; }

    public String getExchangeApiKey() { return exchangeApiKey; }
    public String getExchangeApiUrl() { return exchangeApiUrl; }

    public String getConstellationQueryKey() { return constellationQueryKey; }
    public String getConstellationQueryUrl() { return constellationQueryUrl; }
    public String getConstellationHoroscopeKey() { return constellationHoroscopeKey; }
    public String getConstellationHoroscopeUrl() { return constellationHoroscopeUrl; }

    public String getPhoneApiKey() { return phoneApiKey; }
    public String getPhoneApiUrl() { return phoneApiUrl; }

    public String getQqMusicSearchUrl() { return qqMusicSearchUrl; }
    public String getQqMusicLyricUrl() { return qqMusicLyricUrl; }
    public String getQqMusicReferer() { return qqMusicReferer; }
    public String getQqMusicPlayUrl() { return qqMusicPlayUrl; }
    public String getNeteaseMusicSearchUrl() { return neteaseMusicSearchUrl; }
    public String getNeteaseMusicPlayUrl() { return neteaseMusicPlayUrl; }
    public int getMusicMaxSearchResults() { return musicMaxSearchResults; }

    public String getSinaStockApiUrl() { return sinaStockApiUrl; }
    public String getTencentStockApiUrl() { return tencentStockApiUrl; }
    public String getSinaRefererUrl() { return sinaRefererUrl; }

    public String getIlinkBaseUrl() { return ilinkBaseUrl; }
    public String getIlinkCdnBaseUrl() { return ilinkCdnBaseUrl; }
    public int getIlinkPollTimeoutMs() { return ilinkPollTimeoutMs; }
    public int getIlinkPollIntervalMs() { return ilinkPollIntervalMs; }
    public int getIlinkBotType() { return ilinkBotType; }
    public String getIlinkChannelVersion() { return ilinkChannelVersion; }

    public String getYuanfenjuApiKey() { return yuanfenjuApiKey; }
    public int getFileExtractMaxLength() { return fileExtractMaxLength; }
    public int getFileMaxSize() { return fileMaxSize; }
}
