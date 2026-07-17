package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

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
