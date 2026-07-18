package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 微信消息中的语音项。
 * <p>
 * voiceUrl: 语音直链下载地址（旧格式，可能为空）；
 * media: CDN 媒体元数据（包含 encrypt_query_param、aes_key 等）；
 * duration: 语音时长（秒）。
 * <p>
 * 和图片类似，语音也走微信 CDN 加密传输，优先使用 media 字段构造下载 URL。
 */
public record VoiceItem(
    @JsonProperty("voice_url") String voiceUrl,
    @JsonProperty("media") CDNMedia media,
    Integer duration
) {}
