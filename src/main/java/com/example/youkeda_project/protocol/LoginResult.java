package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginResult(
    String status,
    @JsonProperty("bot_token") String botToken,
    @JsonProperty("ilink_bot_id") String botId,
    @JsonProperty("ilink_user_id") String userId,
    String baseurl
) {}
