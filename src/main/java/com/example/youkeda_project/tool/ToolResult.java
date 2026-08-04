package com.example.youkeda_project.tool;

import java.util.List;

public class ToolResult {

    private final String text;
    private final String imageUrl;
    private final byte[] imageBytes;
    private final List<byte[]> imageBytesList;
    private final byte[] audioBytes;
    private final String audioFileName;
    private final int audioDurationSeconds;
    /** 随图片直接发送给用户的说明文本(不经过AI改写),如图例说明 */
    private final String caption;

    private ToolResult(String text, String imageUrl, byte[] imageBytes, List<byte[]> imageBytesList,
                       byte[] audioBytes, String audioFileName, int audioDurationSeconds, String caption) {
        this.text = text;
        this.imageUrl = imageUrl;
        this.imageBytes = imageBytes;
        this.imageBytesList = imageBytesList != null && !imageBytesList.isEmpty() ? imageBytesList : null;
        this.audioBytes = audioBytes;
        this.audioFileName = audioFileName;
        this.audioDurationSeconds = audioDurationSeconds;
        this.caption = caption;
    }

    public static ToolResult text(String text) {
        return new ToolResult(text, null, null, null, null, null, 0, null);
    }

    public static ToolResult image(String text, String imageUrl) {
        return new ToolResult(text, imageUrl, null, null, null, null, 0, null);
    }

    public static ToolResult imageBytes(String text, byte[] imageBytes) {
        return new ToolResult(text, null, imageBytes, null, null, null, 0, null);
    }

    /** 多张图片一起返回（如图表对比的折线图 + K线图）。 */
    public static ToolResult images(String text, List<byte[]> imageBytesList) {
        return new ToolResult(text, null, null, imageBytesList, null, null, 0, null);
    }

    /** 多张图片 + 随图直接发送的说明文本(不经过AI). */
    public static ToolResult imagesWithCaption(String text, List<byte[]> imageBytesList, String caption) {
        return new ToolResult(text, null, null, imageBytesList, null, null, 0, caption);
    }

    public static ToolResult audio(String text, byte[] audioBytes, String audioFileName, int durationSeconds) {
        return new ToolResult(text, null, null, null, audioBytes, audioFileName, durationSeconds, null);
    }

    public String getText() { return text; }
    public String getImageUrl() { return imageUrl; }
    public byte[] getImageBytes() { return imageBytes; }
    public List<byte[]> getImageBytesList() { return imageBytesList; }
    public byte[] getAudioBytes() { return audioBytes; }
    public String getAudioFileName() { return audioFileName; }
    public int getAudioDurationSeconds() { return audioDurationSeconds; }
    public String getCaption() { return caption; }

    public boolean hasImage() {
        return (imageUrl != null && !imageUrl.isBlank())
            || (imageBytes != null && imageBytes.length > 0)
            || (imageBytesList != null && !imageBytesList.isEmpty());
    }
    public boolean hasAudio() { return audioBytes != null && audioBytes.length > 0; }
}
