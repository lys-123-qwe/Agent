package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 微信消息中的语音项。
 * voiceUrl: 语音文件下载地址；
 * duration: 语音时长（秒）。
 */
public record VoiceItem(
    @JsonProperty("voice_url") String voiceUrl,
    Integer duration
) {}
