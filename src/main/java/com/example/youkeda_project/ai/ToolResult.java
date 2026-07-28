package com.example.youkeda_project.ai;

public class ToolResult {

    private final String text;
    private final String imageUrl;
    private final byte[] imageBytes;
    private final byte[] audioBytes;
    private final String audioFileName;
    private final int audioDurationSeconds;

    private ToolResult(String text, String imageUrl, byte[] imageBytes, byte[] audioBytes, String audioFileName, int audioDurationSeconds) {
        this.text = text;
        this.imageUrl = imageUrl;
        this.imageBytes = imageBytes;
        this.audioBytes = audioBytes;
        this.audioFileName = audioFileName;
        this.audioDurationSeconds = audioDurationSeconds;
    }

    public static ToolResult text(String text) {
        return new ToolResult(text, null, null, null, null, 0);
    }

    public static ToolResult image(String text, String imageUrl) {
        return new ToolResult(text, imageUrl, null, null, null, 0);
    }

    public static ToolResult imageBytes(String text, byte[] imageBytes) {
        return new ToolResult(text, null, imageBytes, null, null, 0);
    }

    public static ToolResult audio(String text, byte[] audioBytes, String audioFileName, int durationSeconds) {
        return new ToolResult(text, null, null, audioBytes, audioFileName, durationSeconds);
    }

    public String getText() { return text; }
    public String getImageUrl() { return imageUrl; }
    public byte[] getImageBytes() { return imageBytes; }
    public byte[] getAudioBytes() { return audioBytes; }
    public String getAudioFileName() { return audioFileName; }
    public int getAudioDurationSeconds() { return audioDurationSeconds; }

    public boolean hasImage() { return (imageUrl != null && !imageUrl.isBlank()) || (imageBytes != null && imageBytes.length > 0); }
    public boolean hasAudio() { return audioBytes != null && audioBytes.length > 0; }
}
