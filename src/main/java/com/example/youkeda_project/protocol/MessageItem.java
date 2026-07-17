package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MessageItem(
    int type,
    @JsonProperty("text_item") TextItem textItem,
    @JsonProperty("image_item") ImageItem imageItem,
    @JsonProperty("voice_item") VoiceItem voiceItem
) {
    public static MessageItem text(String text) {
        return new MessageItem(1, new TextItem(text), null, null);
    }

    public static MessageItem cdnImage(CDNMedia media, int midSize) {
        return new MessageItem(2, null, ImageItem.fromCdn(media, midSize), null);
    }
}
