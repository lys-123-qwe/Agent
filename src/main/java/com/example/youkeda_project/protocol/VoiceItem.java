package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VoiceItem(
    @JsonProperty("voice_url") String voiceUrl,
    Integer duration
) {}
