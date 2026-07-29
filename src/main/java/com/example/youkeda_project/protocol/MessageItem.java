package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 微信消息中的单项内容.
 * <p>
 * type: 1=文本, 2=图片, 3=语音;
 * 根据 type 不同,只有对应的 xxxItem 字段非空.
 * <p>
 * 静态工厂方法 text() / cdnImage() 用于快速构造发送消息.
 */
public record MessageItem(
    int type,
    @JsonProperty("text_item") TextItem textItem,
    @JsonProperty("image_item") ImageItem imageItem,
    @JsonProperty("voice_item") VoiceItem voiceItem,
    @JsonProperty("file_item") FileItem fileItem
) {
    public static MessageItem text(String text) {
        return new MessageItem(1, new TextItem(text), null, null, null);
    }

    public static MessageItem cdnImage(CDNMedia media, int midSize) {
        return new MessageItem(2, null, ImageItem.fromCdn(media, midSize), null, null);
    }

    public static MessageItem cdnVoice(CDNMedia media, String voiceUrl, Integer duration) {
        return new MessageItem(3, null, null, new VoiceItem(voiceUrl, media, duration), null);
    }

    public static MessageItem cdnFile(CDNMedia media, String fileName, long fileSize) {
        return new MessageItem(4, null, null, null, new FileItem(media, fileName, String.valueOf(fileSize)));
    }
}
