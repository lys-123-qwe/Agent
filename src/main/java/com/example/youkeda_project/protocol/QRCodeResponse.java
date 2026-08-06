package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * iLink 获取登录二维码的响应.
 * <p>
 * qrcode: 二维码 ID,用于轮询扫码状态;
 * qrcodeImgContent: 二维码图片内容(base64 或 URL),展示给用户扫码.
 */

public record QRCodeResponse(
    String qrcode,
    @JsonProperty("qrcode_img_content") String qrcodeImgContent
) {}
