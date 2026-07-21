package com.example.youkeda_project.ai;

/**
 * 工具执行结果。
 * <p>
 * 除了返回给 LLM 的文本外，还可携带需要由调用方处理的媒体数据
 * （如图片 URL、音频字节），用于组合任务中的 CDN 上传和发送。
 */
public class ToolResult {

    private final String text;
    private final String imageUrl;       // 工具生成的图片 OSS URL（可选）
    private final byte[] audioBytes;     // 工具合成的音频字节（可选）
    private final String audioFileName;  // 音频文件名（可选）

    private ToolResult(String text, String imageUrl, byte[] audioBytes, String audioFileName) {
        this.text = text;
        this.imageUrl = imageUrl;
        this.audioBytes = audioBytes;
        this.audioFileName = audioFileName;
    }

    /** 纯文本结果 */
    public static ToolResult text(String text) {
        return new ToolResult(text, null, null, null);
    }

    /** 工具生成了图片，返回结果文本 + 图片 URL */
    public static ToolResult image(String text, String imageUrl) {
        return new ToolResult(text, imageUrl, null, null);
    }

    /** 工具合成了音频，返回结果文本 + 音频字节 */
    public static ToolResult audio(String text, byte[] audioBytes, String audioFileName) {
        return new ToolResult(text, null, audioBytes, audioFileName);
    }

    public String getText() { return text; }
    public String getImageUrl() { return imageUrl; }
    public byte[] getAudioBytes() { return audioBytes; }
    public String getAudioFileName() { return audioFileName; }

    public boolean hasImage() { return imageUrl != null && !imageUrl.isBlank(); }
    public boolean hasAudio() { return audioBytes != null && audioBytes.length > 0; }
}
