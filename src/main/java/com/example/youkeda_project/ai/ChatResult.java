package com.example.youkeda_project.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * AIService.chat() 的返回结果。
 * <p>
 * 包含 LLM 的文本回复以及工具执行产生的 pending 媒体操作
 * （如图片 CDN 上传、音频文件发送），供调用方处理。
 */
public class ChatResult {

    private final String replyText;
    private final List<ToolResult> pendingMedia;
    private final String rawToolResult;

    public ChatResult(String replyText, List<ToolResult> pendingMedia) {
        this.replyText = replyText;
        this.pendingMedia = pendingMedia != null ? pendingMedia : List.of();
        this.rawToolResult = null;
    }

    public ChatResult(String replyText) {
        this(replyText, List.of());
    }

    public ChatResult(String replyText, List<ToolResult> pendingMedia, String rawToolResult) {
        this.replyText = replyText;
        this.pendingMedia = pendingMedia != null ? pendingMedia : List.of();
        this.rawToolResult = rawToolResult;
    }

    public String getReplyText() { return replyText; }
    public List<ToolResult> getPendingMedia() { return pendingMedia; }
    public String getRawToolResult() { return rawToolResult; }

    /** 是否有图片需要 CDN 上传 */
    public boolean hasPendingImage() {
        return pendingMedia.stream().anyMatch(ToolResult::hasImage);
    }

    /** 是否有音频需要发送 */
    public boolean hasPendingAudio() {
        return pendingMedia.stream().anyMatch(ToolResult::hasAudio);
    }

    /** 获取待上传的第一张图片 URL */
    public String getFirstPendingImageUrl() {
        return pendingMedia.stream()
                .filter(ToolResult::hasImage)
                .map(ToolResult::getImageUrl)
                .findFirst().orElse(null);
    }

    /** 获取待发送的第一个音频 */
    public ToolResult getFirstPendingAudio() {
        return pendingMedia.stream()
                .filter(ToolResult::hasAudio)
                .findFirst().orElse(null);
    }

    public static Builder builder(String replyText) {
        return new Builder(replyText);
    }

    public static class Builder {
        private final String replyText;
        private final List<ToolResult> pendingMedia = new ArrayList<>();

        Builder(String replyText) { this.replyText = replyText; }

        public Builder addMedia(ToolResult result) {
            if (result != null && (result.hasImage() || result.hasAudio())) {
                pendingMedia.add(result);
            }
            return this;
        }

        public ChatResult build() {
            return new ChatResult(replyText, pendingMedia);
        }
    }
}
