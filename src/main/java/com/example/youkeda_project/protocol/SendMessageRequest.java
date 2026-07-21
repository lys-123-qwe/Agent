package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * iLink sendMessage 请求体。
 * <p>
 * 包含 Msg 内部记录（收件人、contextToken、内容列表）和 base_info。
 * 每次请求生成唯一 client_id（UUID），用于去重和追踪。
 * <p>
 * 静态工厂方法: text() / cdnImage() / items()。
 */
public record SendMessageRequest(
    Msg msg,
    @JsonProperty("base_info") Map<String, Object> baseInfo
) {
    private static final Map<String, Object> BASE_INFO = Map.of("channel_version", "1.0.2");

    public static SendMessageRequest items(String toUserId, String contextToken, List<MessageItem> itemList) {
        return new SendMessageRequest(
            new Msg("", toUserId, UUID.randomUUID().toString(), 2, 2, contextToken, itemList),
            BASE_INFO
        );
    }

    public static SendMessageRequest text(String toUserId, String contextToken, String text) {
        return items(toUserId, contextToken, List.of(MessageItem.text(text)));
    }

    public static SendMessageRequest cdnImage(String toUserId, String contextToken, CDNMedia media, int midSize) {
        return items(toUserId, contextToken, List.of(MessageItem.cdnImage(media, midSize)));
    }

    public static SendMessageRequest cdnVoice(String toUserId, String contextToken, CDNMedia media, String voiceUrl, Integer duration) {
        return items(toUserId, contextToken, List.of(MessageItem.cdnVoice(media, voiceUrl, duration)));
    }

    public static SendMessageRequest cdnFile(String toUserId, String contextToken, CDNMedia media, String fileName, long fileSize) {
        return items(toUserId, contextToken, List.of(MessageItem.cdnFile(media, fileName, fileSize)));
    }

    /**
     * 消息内部结构。
     * fromUserId: 发送方（Bot）ID，通常填空字符串由服务端填充；
     * toUserId: 接收方用户 ID；
     * clientId: 客户端去重 ID（UUID）；
     * messageType: 消息类型（2=普通消息）；
     * messageState: 消息状态（2=发送）；
     * contextToken: 回复消息时必须带的上下文 Token；
     * itemList: 消息内容列表（文本、图片等）。
     */
    public record Msg(
        @JsonProperty("from_user_id") String fromUserId,
        @JsonProperty("to_user_id") String toUserId,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("message_type") int messageType,
        @JsonProperty("message_state") int messageState,
        @JsonProperty("context_token") String contextToken,
        @JsonProperty("item_list") List<MessageItem> itemList
    ) {}
}
