package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoginResult {

    private String status;

    @JsonProperty("bot_token")
    private String botToken;

    @JsonProperty("ilink_bot_id")
    private String botId;

    @JsonProperty("ilink_user_id")
    private String userId;

    private String baseurl;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public String getBotId() {
        return botId;
    }

    public void setBotId(String botId) {
        this.botId = botId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getBaseurl() {
        return baseurl;
    }

    public void setBaseurl(String baseurl) {
        this.baseurl = baseurl;
    }
}