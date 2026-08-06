package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 微信消息中的图片项.
 * <p>
 * media: CDN 媒体元数据;
 * aeskey: 图片 AES 解密密钥(可能在此字段也可能在 CDNMedia 中,两处兼容);
 * url: 图片直链(旧格式).
 * <p>
 * fromCdn() 用于构造发送图片时的 ImageItem.
 */

public record ImageItem(
    @JsonProperty("media") CDNMedia media,
    @JsonProperty("mid_size") Integer midSize,
    @JsonProperty("hd_size") Integer hdSize,
    @JsonProperty("aeskey") String aeskey,
    @JsonProperty("url") String url,
    @JsonProperty("thumb_width") Integer thumbWidth,
    @JsonProperty("thumb_height") Integer thumbHeight
) {
    public static ImageItem fromCdn(CDNMedia media, int midSize) {
        return new ImageItem(media, midSize, null, null, null, null, null);
    }
}
