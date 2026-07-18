package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * iLink 扫码登录结果。
 * <p>
 * status: confirmed（成功）/ expired（过期）/ waiting（等待扫码）/ scanned（已扫码）；
 * botToken: 登录后的 Bot 鉴权 Token；
 * botId: Bot 唯一标识；
 * userId: Bot 所有者的微信用户 ID；
 * baseurl: iLink API 地址（可能因环境不同）。
 */
public record LoginResult(
    String status,
    @JsonProperty("bot_token") String botToken,
    @JsonProperty("ilink_bot_id") String botId,
    @JsonProperty("ilink_user_id") String userId,
    String baseurl
) {}
